// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

class NegativeEdgeLatch(clock: Clock, width: Int) extends Module(override_clock=Some(clock)) {
  class IoClass extends Bundle {
    val input = Input(UInt(width.W))
    val output = Output(UInt(width.W))
  }
  val io = IO(new IoClass)

  val reg = Reg(UInt(width.W))
  reg := io.input
  io.output := reg
}

/** Generates a register that updates on the falling edge of the input clock signal.
  */
object NegativeEdgeLatch {
  def apply(clock: Clock, signal: Data, width: Int): UInt = {
    val latch_module = Module(new NegativeEdgeLatch((!clock.asUInt).asClock, width))
    latch_module.io.input := signal
    latch_module.io.output
  }
}

object JtagState {
  sealed abstract class State(val id: Int) {
    def U: UInt = id.U
  }

  object State {
    import scala.language.implicitConversions

    implicit def toInt(x: State) = x.id
    implicit def toBigInt(x: State):BigInt = x.id

    // TODO: this could be automatically generated with macros and stuff
    val all: Set[State] = Set(
      TestLogicReset,
      RunTestIdle,
      SelectDRScan,
      CaptureDR,
      ShiftDR,
      Exit1DR,
      PauseDR,
      Exit2DR,
      UpdateDR,
      SelectIRScan,
      CaptureIR,
      ShiftIR,
      Exit1IR,
      PauseIR,
      Exit2IR,
      UpdateIR
    )
    val width = log2Up(all.size)
    def chiselType() = UInt(width.W)
  }

  case object TestLogicReset extends State(0)
  case object RunTestIdle extends State(1)
  case object SelectDRScan extends State(2)
  case object CaptureDR extends State(3)
  case object ShiftDR extends State(4)
  case object Exit1DR extends State(5)
  case object PauseDR extends State(6)
  case object Exit2DR extends State(7)
  case object UpdateDR extends State(8)
  case object SelectIRScan extends State(9)
  case object CaptureIR extends State(10)
  case object ShiftIR extends State(11)
  case object Exit1IR extends State(12)
  case object PauseIR extends State(13)
  case object Exit2IR extends State(14)
  case object UpdateIR extends State(15)
}

/** JTAG signals, viewed from the device side.
  */
class JtagIO extends Bundle {
  // TRST (4.6) is optional and not currently implemented.
  val TCK = Input(Bool())
  val TMS = Input(Bool())
  val TDI = Input(Bool())
  val TDO = Output(Bool())
}

/** JTAG block internal status information, for testing purposes.
  */
class JtagStatus extends Bundle {
  val state = Output(JtagState.State.chiselType())
}

/** Aggregate JTAG block IO
  */
class JtagBlockIO extends Bundle {
  val jtag = new JtagIO
  val status = new JtagStatus
}

class JtagStateMachine extends Module {
  class StateMachineIO extends Bundle {
    val TMS = Input(Bool())
    val currState = Output(JtagState.State.chiselType())
    val nextState = Output(JtagState.State.chiselType())
  }
  val io = IO(new StateMachineIO)

  val currState = Reg(JtagState.State.chiselType(), init=JtagState.TestLogicReset.U)
  val nextState = Wire(JtagState.State.chiselType())
  currState := nextState
  io.currState := currState
  io.nextState := nextState

  switch (currState) {
    is (JtagState.TestLogicReset.U) {
      nextState := Mux(io.TMS, JtagState.TestLogicReset.U, JtagState.RunTestIdle.U)
    }
    is (JtagState.RunTestIdle.U) {
      nextState := Mux(io.TMS, JtagState.SelectDRScan.U, JtagState.RunTestIdle.U)
    }
    is (JtagState.SelectDRScan.U) {
      nextState := Mux(io.TMS, JtagState.SelectIRScan.U, JtagState.CaptureDR.U)
    }
    is (JtagState.CaptureDR.U) {
      nextState := Mux(io.TMS, JtagState.Exit1DR.U, JtagState.ShiftDR.U)
    }
    is (JtagState.ShiftDR.U) {
      nextState := Mux(io.TMS, JtagState.Exit1DR.U, JtagState.ShiftDR.U)
    }
    is (JtagState.Exit1DR.U) {
      nextState := Mux(io.TMS, JtagState.UpdateDR.U, JtagState.PauseDR.U)
    }
    is (JtagState.PauseDR.U) {
      nextState := Mux(io.TMS, JtagState.Exit2DR.U, JtagState.PauseDR.U)
    }
    is (JtagState.Exit2DR.U) {
      nextState := Mux(io.TMS, JtagState.UpdateDR.U, JtagState.ShiftDR.U)
    }
    is (JtagState.UpdateDR.U) {
      nextState := Mux(io.TMS, JtagState.SelectDRScan.U, JtagState.RunTestIdle.U)
    }
    is (JtagState.SelectIRScan.U) {
      nextState := Mux(io.TMS, JtagState.TestLogicReset.U, JtagState.CaptureIR.U)
    }
    is (JtagState.CaptureIR.U) {
      nextState := Mux(io.TMS, JtagState.Exit1IR.U, JtagState.ShiftIR.U)
    }
    is (JtagState.ShiftIR.U) {
      nextState := Mux(io.TMS, JtagState.Exit1IR.U, JtagState.ShiftIR.U)
    }
    is (JtagState.Exit1IR.U) {
      nextState := Mux(io.TMS, JtagState.UpdateIR.U, JtagState.PauseIR.U)
    }
    is (JtagState.PauseIR.U) {
      nextState := Mux(io.TMS, JtagState.Exit2IR.U, JtagState.PauseIR.U)
    }
    is (JtagState.Exit2IR.U) {
      nextState := Mux(io.TMS, JtagState.UpdateIR.U, JtagState.ShiftIR.U)
    }
    is (JtagState.UpdateIR.U) {
      nextState := Mux(io.TMS, JtagState.SelectDRScan.U, JtagState.RunTestIdle.U)
    }
  }
}

/** JTAG TAP internal block, that has a overridden clock so registers can be clocked on TCK rising.
  */
class JtagTapInternal(mod_clock: Clock) extends Module(override_clock=Some(mod_clock)) {
  val io = IO(new JtagBlockIO)

  val tms = Reg(Bool(), next=io.jtag.TMS)  // 4.3.1a captured on TCK rising edge
  val tdi = Reg(Bool(), next=io.jtag.TDI)  // 4.3.2a captured on TCK rising edge
  val tdo = Wire(Bool())  // 4.4.1c TDI should appear here uninverted after shifting
  io.jtag.TDO := NegativeEdgeLatch(clock, tdo, 1)  // 4.5.1a TDO changes on falling edge of TCK or TRST

  tdo := tdi  // test
}

/** JTAG TAP block, clocked from TCK.
  *
  * Usage notes:
  * - 4.3.1b TMS must appear high when undriven
  * - 4.3.1c (rec) minimize load presented by TMS
  * - 4.4.1b TDI must appear high when undriven
  * - 4.5.1b TDO must be inactive except when shifting data (undriven? 6.1.2)
  */
class JtagTap() extends Module {
  val io = IO(new JtagBlockIO)

  val tap = Module(new JtagTapInternal(io.jtag.TCK.asClock))
  io <> tap.io
}
