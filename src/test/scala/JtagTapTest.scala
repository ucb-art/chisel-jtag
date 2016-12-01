// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

trait TristateTestUtility extends PeekPokeTester[chisel3.Module] {
  import scala.language.implicitConversions

  trait TristateValue
  case object TristateLow extends TristateValue
  case object TristateHigh extends TristateValue
  case object Z extends TristateValue
  case object X extends TristateValue

  implicit def toTristateValue(x: Int) : TristateValue = {
    x match {
      case 0 => TristateLow
      case 1 => TristateHigh
    }
  }

  def expect(node: Tristate, value: TristateValue, msg: String) {
    value match {
      case TristateLow => {
        expect(node.driven, 1, s"$msg: expected tristate driven=1")
        expect(node.data, 0, s"$msg: expected tristate data=0")
      }
      case TristateHigh => {
        expect(node.driven, 1, s"$msg: expected tristate driven=1")
        expect(node.data, 1, s"$msg: expected tristate data=1")
      }
      case Z => {
        expect(node.driven, 0, s"$msg: expected tristate driven=0")
      }
    }
  }

  def poke(node: Bool, value: TristateValue) {
    value match {
      case TristateLow => poke(node, 0)
      case TristateHigh => poke(node, 1)
      case X => poke(node, 0)
    }
  }
}

trait JtagTestUtilities extends PeekPokeTester[chisel3.Module] with TristateTestUtility {
  val jtag: JtagIO
  val status: JtagStatus

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

    poke(jtag.TCK, 1)
    step(1)
    expect(jtag.TDO, expectedTdo, s"$msg: TDO")
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

class JtagTapTester(val c: JtagTap) extends PeekPokeTester(c) with JtagTestUtilities {
  val jtag = c.io.jtag
  val status = c.io.status

  tmsReset()

  // Test sequence in Figure 6-3 (instruction scan), starting with the half-cycle off-screen
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
  jtagCycle(1, JtagState.ShiftIR, tdi=0, expectedTdo=1)
  jtagCycle(1, JtagState.Exit1IR)
  jtagCycle(0, JtagState.UpdateIR)
}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP" should "work" in {
    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => new JtagTap(2, Map(0.U -> 0)), backendType="verilator") {
      c => new JtagTapTester(c)
    } should be (true)
  }
}
