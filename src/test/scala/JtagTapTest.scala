// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

trait JtagTestUtilities extends PeekPokeTester[chisel3.Module] with TristateTestUtility {
  val jtag: JtagIO
  val output: JtagOutput

  var expectedInstruction: Option[Int] = None  // expected instruction output after TCK low

  /** Convenience function for stepping a JTAG cycle (TCK high -> low -> high) and checking basic
    * JTAG values.
    *
    * @param tms TMS to set after the clock low
    * @param expectedState expected state during this cycle
    * @param tdi TDI to set after the clock low
    * @param expectedTdo expected TDO after the clock low
    */
  def jtagCycle(tms: Int, expectedState: JtagState.State, tdi: TristateValue = X,
      expectedTdo: TristateValue = Z, msg: String = "") {
    expect(jtag.TCK, 1, "TCK must start at 1")
    val prevState = peek(output.state)

    expect(output.state, expectedState, s"$msg: expected state $expectedState")

    poke(jtag.TCK, 0)
    step(1)
    expect(output.state, expectedState, s"$msg: expected state $expectedState")

    poke(jtag.TMS, tms)
    poke(jtag.TDI, tdi)
    expect(jtag.TDO, expectedTdo, s"$msg: TDO")
    expectedInstruction match {
      case Some(instruction) => expect(output.instruction, instruction, s"$msg: expected instruction $instruction")
      case None =>
    }

    poke(jtag.TCK, 1)
    step(1)
    expect(jtag.TDO, expectedTdo, s"$msg: TDO")
  }

  /** After every TCK falling edge following this call, expect this instruction on the status line.
    * None means to not check the instruction output.
    */
  def expectInstruction(expected: Option[Int]) {
    expectedInstruction = expected
  }

  /** Resets the test block using 5 TMS transitions
    */
  def tmsReset() {
    poke(jtag.TMS, 1)
    poke(jtag.TCK, 1)
    step(1)
    for (_ <- 0 until 5) {
      poke(jtag.TCK, 0)
      step(1)
      poke(jtag.TCK, 1)
      step(1)
    }
    expect(output.state, JtagState.TestLogicReset, "TMS reset: expected in reset state")
  }

  def resetToIdle() {
    tmsReset()
    jtagCycle(0, JtagState.TestLogicReset)
    expect(output.state, JtagState.RunTestIdle)
  }

  def idleToDRShift() {
    jtagCycle(1, JtagState.RunTestIdle)
    jtagCycle(0, JtagState.SelectDRScan)
    jtagCycle(0, JtagState.CaptureDR)
    expect(output.state, JtagState.ShiftDR)
  }

  def idleToIRShift() {
    jtagCycle(1, JtagState.RunTestIdle)
    jtagCycle(1, JtagState.SelectDRScan)
    jtagCycle(0, JtagState.SelectIRScan)
    jtagCycle(0, JtagState.CaptureIR)
    expect(output.state, JtagState.ShiftIR)
  }

  def drShiftToIdle() {
    jtagCycle(1, JtagState.Exit1DR)
    jtagCycle(0, JtagState.UpdateDR)
    expect(output.state, JtagState.RunTestIdle)
  }

  def irShiftToIdle() {
    jtagCycle(1, JtagState.Exit1IR)
    jtagCycle(0, JtagState.UpdateIR)
    expect(output.state, JtagState.RunTestIdle)
  }

  /** Shifts data into the TDI register and checks TDO against expected data. Must start in the
    * shift, and the TAP controller will be in the Exit1 state afterwards.
    *
    * TDI and expected TDO are specified as a string of 0, 1, and ?. Spaces are discarded.
    * The strings are in time order, the first elements are the ones shifted out (and expected in)
    * first. This is in waveform display order and LSB-first order (so when specifying a number in
    * the usual MSB-first order, the string needs to be reversed).
    */
  def shift(tdi: String, expectedTdo: String, expectedState: JtagState.State, expectedNextState: JtagState.State) {
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
      jtagCycle(0, expectedState, tdi=tdiBit, expectedTdo=expectedTdoBit)
    }
    val (tdiLastBit, expectedTdoLastBit) = zipBits.last
    jtagCycle(1, expectedState, tdi=tdiLastBit, expectedTdo=expectedTdoLastBit)

    expect(output.state, expectedNextState)
  }

  def drShift(tdi: String, expectedTdo: String) {
    shift(tdi, expectedTdo, JtagState.ShiftDR, JtagState.Exit1DR)
  }

  def irShift(tdi: String, expectedTdo: String) {
    shift(tdi, expectedTdo, JtagState.ShiftIR, JtagState.Exit1IR)
  }
}

class JtagTester[T <: JtagModule](c: JtagClocked[T]) extends PeekPokeTester(c) with JtagTestUtilities {
  val jtag = c.io.jtag
  val output = c.io.output
}

class JtagTapTester(val c: JtagClocked[BareJtagModule]) extends JtagTester(c) {
  import BinaryParse._

  tmsReset()
  expectInstruction(Some("11".b))
  // Test sequence in Figure 6-3 (instruction scan), starting with the half-cycle off-screen
  jtagCycle(1, JtagState.TestLogicReset)
  jtagCycle(0, JtagState.TestLogicReset)
  jtagCycle(1, JtagState.RunTestIdle)
  jtagCycle(1, JtagState.SelectDRScan)
  jtagCycle(0, JtagState.SelectIRScan)
  jtagCycle(0, JtagState.CaptureIR)
  jtagCycle(0, JtagState.ShiftIR, tdi=0, expectedTdo=1)  // first two required IR capture bits
  jtagCycle(1, JtagState.ShiftIR, tdi=0, expectedTdo=0)
  jtagCycle(0, JtagState.Exit1IR)
  jtagCycle(0, JtagState.PauseIR)
  jtagCycle(0, JtagState.PauseIR)
  jtagCycle(1, JtagState.PauseIR)
  jtagCycle(0, JtagState.Exit2IR)
  jtagCycle(0, JtagState.ShiftIR, tdi=1, expectedTdo=0)
  jtagCycle(0, JtagState.ShiftIR, tdi=1, expectedTdo=0)
  jtagCycle(0, JtagState.ShiftIR, tdi=0, expectedTdo=1)
  jtagCycle(1, JtagState.ShiftIR, tdi=1, expectedTdo=1)
  jtagCycle(1, JtagState.Exit1IR)
  expectInstruction(Some("10".b))
  jtagCycle(0, JtagState.UpdateIR)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.RunTestIdle)

  tmsReset()
  expectInstruction(Some("11".b))
  jtagCycle(0, JtagState.TestLogicReset)
  // Test sequence in Figure 6-4 (data scan), starting with the half-cycle off-screen
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(1, JtagState.RunTestIdle)
  jtagCycle(0, JtagState.SelectDRScan)
  jtagCycle(0, JtagState.CaptureDR)
  jtagCycle(0, JtagState.ShiftDR, tdi=1, expectedTdo=0)  // required bypass capture bit
  jtagCycle(0, JtagState.ShiftDR, tdi=0, expectedTdo=1)
  jtagCycle(1, JtagState.ShiftDR, tdi=1, expectedTdo=0)
  jtagCycle(0, JtagState.Exit1DR)
  jtagCycle(0, JtagState.PauseDR)
  jtagCycle(0, JtagState.PauseDR)
  jtagCycle(1, JtagState.PauseDR)
  jtagCycle(0, JtagState.Exit2DR)
  jtagCycle(0, JtagState.ShiftDR, tdi=1, expectedTdo=1)
  jtagCycle(0, JtagState.ShiftDR, tdi=1, expectedTdo=1)
  jtagCycle(0, JtagState.ShiftDR, tdi=0, expectedTdo=1)
  jtagCycle(1, JtagState.ShiftDR, tdi=0, expectedTdo=0)
  jtagCycle(1, JtagState.Exit1DR)
  jtagCycle(0, JtagState.UpdateDR)
  jtagCycle(0, JtagState.RunTestIdle)
  jtagCycle(1, JtagState.RunTestIdle)
  jtagCycle(1, JtagState.SelectDRScan)  // Fig 6-4 says "Select-IR-Scan", seems like a typo
  jtagCycle(1, JtagState.SelectIRScan)
  jtagCycle(1, JtagState.TestLogicReset)
  jtagCycle(1, JtagState.TestLogicReset)
  jtagCycle(1, JtagState.TestLogicReset)
}

trait JtagModule extends Module {
  val io: JtagBlockIO
}

class JtagClocked[T <: JtagModule](gen: ()=>T) extends Module {
  class Reclocked(modClock: Clock) extends Module(override_clock=Some(modClock)) {
    val mod = Module(gen())
    val io = IO(mod.io.cloneType)
    io <> mod.io
  }

  val innerClock = Wire(Bool())
  val clockMod = Module(new Reclocked(innerClock.asClock))

  val io = IO(clockMod.io.cloneType)
  io <> clockMod.io
  innerClock := io.jtag.TCK
}

object JtagClocked {
  def apply[T <: JtagModule](gen: ()=>T): JtagClocked[T] = {
    new JtagClocked(gen)
  }
}

class BareJtagModule(irLength: Int) extends JtagModule {
  val controller = JtagTapGenerator(irLength, Map())
  val io = IO(controller.cloneType)
  io <> controller
}

class JtagTapExampleWaveformSpec extends ChiselFlatSpec {
  "JTAG TAP with example waveforms from the spec" should "work" in {
    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => JtagClocked(() => new BareJtagModule(2)), backendType="verilator") {
      c => new JtagTapTester(c)
    } should be (true)
  }
}
