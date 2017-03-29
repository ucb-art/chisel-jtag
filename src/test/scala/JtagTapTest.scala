// See LICENSE for license details.

package jtag.test

import org.scalatest._

import chisel3.iotesters.experimental.{ImplicitPokeTester, VerilatorBackend}

import chisel3._
import jtag._

trait JtagTestUtilities extends ImplicitPokeTester with TristateTestUtility {
  var expectedInstruction: Option[Int] = None  // expected instruction output after TCK low

  /** Convenience function for stepping a JTAG cycle (TCK high -> low -> high) and checking basic
    * JTAG values.
    *
    * @param tms TMS to set after the clock low
    * @param expectedState expected state during this cycle
    * @param tdi TDI to set after the clock low
    * @param expectedTdo expected TDO after the clock low
    */
  def jtagCycle(io: JtagBlockIO, tms: Int, expectedState: JtagState.State, tdi: TristateValue = X,
      expectedTdo: TristateValue = Z, msg: String = "")(implicit t: InnerTester) {
    check(io.jtag.TCK, 1, "TCK must start at 1")
    check(io.output.state, expectedState, s"$msg: expected state $expectedState")

    poke(io.jtag.TCK, 0)
    step(1)
    check(io.output.state, expectedState, s"$msg: expected state $expectedState")

    poke(io.jtag.TMS, tms)
    poke(io.jtag.TDI, tdi)
    check(io.jtag.TDO, expectedTdo, s"$msg: TDO")
    expectedInstruction match {
      case Some(instruction) =>
        check(io.output.instruction, instruction, s"$msg: expected instruction $instruction")
      case None =>
    }

    poke(io.jtag.TCK, 1)
    step(1)
    check(io.jtag.TDO, expectedTdo, s"$msg: TDO")
  }

  /** After every TCK falling edge following this call, expect this instruction on the status line.
    * None means to not check the instruction output.
    */
  // TODO: GET RID OF THIS, BETTER WAY TO CAPTURE STATE
  def expectInstruction(expected: Option[Int]) {
    expectedInstruction = expected
  }

  /** Resets the test block using 5 TMS transitions.
    */
  def tmsReset(io: JtagBlockIO)(implicit t: InnerTester) {
    poke(io.jtag.TMS, 1)
    poke(io.jtag.TCK, 1)
    step(1)
    for (_ <- 0 until 5) {
      poke(io.jtag.TCK, 0)
      step(1)
      poke(io.jtag.TCK, 1)
      step(1)
    }
    check(io.output.state, JtagState.TestLogicReset, "TMS reset: expected in reset state")
  }

  def resetToIdle(io: JtagBlockIO)(implicit t: InnerTester) {
    tmsReset(io)
    jtagCycle(io, 0, JtagState.TestLogicReset)
    check(io.output.state, JtagState.RunTestIdle)
  }

  def idleToDRShift(io: JtagBlockIO)(implicit t: InnerTester) {
    jtagCycle(io, 1, JtagState.RunTestIdle)
    jtagCycle(io, 0, JtagState.SelectDRScan)
    jtagCycle(io, 0, JtagState.CaptureDR)
    check(io.output.state, JtagState.ShiftDR)
  }

  def idleToIRShift(io: JtagBlockIO)(implicit t: InnerTester) {
    jtagCycle(io, 1, JtagState.RunTestIdle)
    jtagCycle(io, 1, JtagState.SelectDRScan)
    jtagCycle(io, 0, JtagState.SelectIRScan)
    jtagCycle(io, 0, JtagState.CaptureIR)
    check(io.output.state, JtagState.ShiftIR)
  }

  def drShiftToIdle(io: JtagBlockIO)(implicit t: InnerTester) {
    jtagCycle(io, 1, JtagState.Exit1DR)
    jtagCycle(io, 0, JtagState.UpdateDR)
    check(io.output.state, JtagState.RunTestIdle)
  }

  def irShiftToIdle(io: JtagBlockIO)(implicit t: InnerTester) {
    jtagCycle(io, 1, JtagState.Exit1IR)
    jtagCycle(io, 0, JtagState.UpdateIR)
    check(io.output.state, JtagState.RunTestIdle)
  }

  /** Shifts data into the TDI register and checks TDO against expected data. Must start in the
    * shift, and the TAP controller will be in the Exit1 state afterwards.
    *
    * TDI and expected TDO are specified as a string of 0, 1, and ?. Spaces are discarded.
    * The strings are in time order, the first elements are the ones shifted out (and expected in)
    * first. This is in waveform display order and LSB-first order (so when specifying a number in
    * the usual MSB-first order, the string needs to be reversed).
    */
  def shift(io: JtagBlockIO, tdi: String, expectedTdo: String, expectedState: JtagState.State, expectedNextState: JtagState.State)
  (implicit t: InnerTester) {
    def charToTristate(x: Char): TristateValue = x match {
      case '0' => 0
      case '1' => 1
      case '?' => X
    }

    val tdiBits = tdi.replaceAll(" ", "") map charToTristate
    val expectedTdoBits = expectedTdo.replaceAll(" ", "") map charToTristate

    require(tdiBits.size == expectedTdoBits.size)
    val zipBits = tdiBits zip expectedTdoBits

    for ((tdiBit, expectedTdoBit) <- zipBits.init) {
      jtagCycle(io, 0, expectedState, tdi=tdiBit, expectedTdo=expectedTdoBit)
    }
    val (tdiLastBit, expectedTdoLastBit) = zipBits.last
    jtagCycle(io, 1, expectedState, tdi=tdiLastBit, expectedTdo=expectedTdoLastBit)

    check(io.output.state, expectedNextState)
  }

  def drShift(io: JtagBlockIO, tdi: String, expectedTdo: String)(implicit t: InnerTester) {
    shift(io, tdi, expectedTdo, JtagState.ShiftDR, JtagState.Exit1DR)
  }

  def irShift(io: JtagBlockIO, tdi: String, expectedTdo: String)(implicit t: InnerTester) {
    shift(io, tdi, expectedTdo, JtagState.ShiftIR, JtagState.Exit1IR)
  }
}

trait JtagModule extends Module {
  val io: JtagBlockIO
}

class JtagClocked[T <: JtagModule](name: String, gen: ()=>T) extends Module {
  class Reclocked(modClock: Clock, modReset: Bool)
      extends Module(override_clock=Some(modClock), override_reset=Some(modReset)) {
    val mod = Module(gen())
    val io = IO(mod.io.cloneType)
    io <> mod.io
  }

  val innerClock = Wire(Bool())
  val innerReset = Wire(Bool())
  val clockMod = Module(new Reclocked(innerClock.asClock, innerReset))

  val io = IO(clockMod.io.cloneType)
  io <> clockMod.io
  innerClock := io.jtag.TCK
  innerReset := clockMod.io.output.reset

  clockMod.io.control.fsmAsyncReset := false.B
  
  override def desiredName = name  // TODO needed to not break verilator because of name aliasing
}

object JtagClocked {
  def apply[T <: JtagModule](name: String, gen: ()=>T): JtagClocked[T] = {
    new JtagClocked(name, gen)
  }
}

class JtagTapExampleWaveformSpec extends FlatSpec with JtagTestUtilities {
  "JTAG TAP with example waveforms from the spec" should "work" in {
    test(JtagClocked("exampleWaveforms", () => new JtagModule {
      // Explicitly create bypass chain instruction to ignore errors about no instructions
      val controller = JtagTapGenerator(2, Map(0 -> Module(new JtagBypassChain)))
      val io = IO(controller.cloneType)
      io <> controller
    }), testerBackend=VerilatorBackend) { implicit t => c =>
      import BinaryParse._
    
      tmsReset(c.io)
      expectInstruction(Some("11".b))
      // Test sequence in Figure 6-3 (instruction scan), starting with the half-cycle off-screen
      jtagCycle(c.io, 1, JtagState.TestLogicReset)
      jtagCycle(c.io, 0, JtagState.TestLogicReset)
      jtagCycle(c.io, 1, JtagState.RunTestIdle)
      jtagCycle(c.io, 1, JtagState.SelectDRScan)
      jtagCycle(c.io, 0, JtagState.SelectIRScan)
      jtagCycle(c.io, 0, JtagState.CaptureIR)
      jtagCycle(c.io, 0, JtagState.ShiftIR, tdi=0, expectedTdo=1)  // first two required IR capture bits
      jtagCycle(c.io, 1, JtagState.ShiftIR, tdi=0, expectedTdo=0)
      jtagCycle(c.io, 0, JtagState.Exit1IR)
      jtagCycle(c.io, 0, JtagState.PauseIR)
      jtagCycle(c.io, 0, JtagState.PauseIR)
      jtagCycle(c.io, 1, JtagState.PauseIR)
      jtagCycle(c.io, 0, JtagState.Exit2IR)
      jtagCycle(c.io, 0, JtagState.ShiftIR, tdi=1, expectedTdo=0)
      jtagCycle(c.io, 0, JtagState.ShiftIR, tdi=1, expectedTdo=0)
      jtagCycle(c.io, 0, JtagState.ShiftIR, tdi=0, expectedTdo=1)
      jtagCycle(c.io, 1, JtagState.ShiftIR, tdi=1, expectedTdo=1)
      jtagCycle(c.io, 1, JtagState.Exit1IR)
      expectInstruction(Some("10".b))
      jtagCycle(c.io, 0, JtagState.UpdateIR)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
    
      tmsReset(c.io)
      expectInstruction(Some("11".b))
      jtagCycle(c.io, 0, JtagState.TestLogicReset)
      // Test sequence in Figure 6-4 (data scan), starting with the half-cycle off-screen
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 1, JtagState.RunTestIdle)
      jtagCycle(c.io, 0, JtagState.SelectDRScan)
      jtagCycle(c.io, 0, JtagState.CaptureDR)
      jtagCycle(c.io, 0, JtagState.ShiftDR, tdi=1, expectedTdo=0)  // required bypass capture bit
      jtagCycle(c.io, 0, JtagState.ShiftDR, tdi=0, expectedTdo=1)
      jtagCycle(c.io, 1, JtagState.ShiftDR, tdi=1, expectedTdo=0)
      jtagCycle(c.io, 0, JtagState.Exit1DR)
      jtagCycle(c.io, 0, JtagState.PauseDR)
      jtagCycle(c.io, 0, JtagState.PauseDR)
      jtagCycle(c.io, 1, JtagState.PauseDR)
      jtagCycle(c.io, 0, JtagState.Exit2DR)
      jtagCycle(c.io, 0, JtagState.ShiftDR, tdi=1, expectedTdo=1)
      jtagCycle(c.io, 0, JtagState.ShiftDR, tdi=1, expectedTdo=1)
      jtagCycle(c.io, 0, JtagState.ShiftDR, tdi=0, expectedTdo=1)
      jtagCycle(c.io, 1, JtagState.ShiftDR, tdi=0, expectedTdo=0)
      jtagCycle(c.io, 1, JtagState.Exit1DR)
      jtagCycle(c.io, 0, JtagState.UpdateDR)
      jtagCycle(c.io, 0, JtagState.RunTestIdle)
      jtagCycle(c.io, 1, JtagState.RunTestIdle)
      jtagCycle(c.io, 1, JtagState.SelectDRScan)  // Fig 6-4 says "Select-IR-Scan", seems like a typo
      jtagCycle(c.io, 1, JtagState.SelectIRScan)
      jtagCycle(c.io, 1, JtagState.TestLogicReset)
      jtagCycle(c.io, 1, JtagState.TestLogicReset)
      jtagCycle(c.io, 1, JtagState.TestLogicReset)
    }
  }
}
