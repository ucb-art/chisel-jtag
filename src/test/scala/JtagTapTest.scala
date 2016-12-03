// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

trait JtagTestUtilities extends PeekPokeTester[chisel3.Module] with TristateTestUtility {
  val jtag: JtagIO
  val status: JtagStatus

  var expectedInstruction: Option[Int] = None  // expected instruction (status.instruction) after TCK low

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
    val prevState = peek(status.state)

    expect(status.state, expectedState, s"$msg: expected state $expectedState")

    poke(jtag.TCK, 0)
    step(1)
    expect(status.state, expectedState, s"$msg: expected state $expectedState")

    poke(jtag.TMS, tms)
    poke(jtag.TDI, tdi)
    expect(jtag.TDO, expectedTdo, s"$msg: TDO")
    expectedInstruction match {
      case Some(instruction) => expect(status.instruction, instruction, s"$msg: expected instruction $instruction")
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
    expect(status.state, JtagState.TestLogicReset, "TMS reset: expected in reset state")
  }
}

class JtagTapTester(val c: JtagTapModule) extends PeekPokeTester(c) with JtagTestUtilities {
  import BinaryParse._

  val jtag = c.io.jtag
  val status = c.io.status

  tmsReset()

  // Test sequence in Figure 6-3 (instruction scan), starting with the half-cycle off-screen
  expectInstruction(Some("00".b))
  jtagCycle(1, JtagState.TestLogicReset)
  jtagCycle(0, JtagState.TestLogicReset)
  jtagCycle(1, JtagState.RunTestIdle)
  jtagCycle(1, JtagState.SelectDRScan)
  jtagCycle(0, JtagState.SelectIRScan)
  jtagCycle(0, JtagState.CaptureIR)
  jtagCycle(0, JtagState.ShiftIR, tdi=0, expectedTdo=1)
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
}

class JtagTapModule(irLength: Int, instructions: Map[Int, Int]) extends Module {
  class JtagTapClocked (modClock: Clock) extends Module(override_clock=Some(modClock)) {
    val io = IO(new JtagBlockIO(irLength, instructions))

    val tap = Module(new JtagTapController(irLength, instructions))
    io <> tap.io
  }

  val io = IO(new JtagBlockIO(irLength, instructions))

  val tap = Module(new JtagTapClocked(io.jtag.TCK.asClock))
  io <> tap.io
}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP" should "work" in {
    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => new JtagTapModule(2, Map(0 -> 0)), backendType="verilator") {
      c => new JtagTapTester(c)
    } should be (true)
  }
}
