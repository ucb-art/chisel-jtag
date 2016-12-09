// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

object JtagState {
  sealed abstract class State(val id: Int) {
    def U: UInt = id.U(State.width.W)
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

  // States as described in 6.1.1.2, numeric assignments from example in Table 6-3
  case object TestLogicReset extends State(15)  // no effect on system logic, entered when TMS high for 5 TCK rising edges
  case object RunTestIdle extends State(12)  // runs active instruction (which can be idle)
  case object SelectDRScan extends State(7)
  case object CaptureDR extends State(6)  // parallel-load DR shifter when exiting this state (if required)
  case object ShiftDR extends State(2)  // shifts DR shifter from TDI towards TDO, last shift occurs on rising edge transition out of this state
  case object Exit1DR extends State(1)
  case object PauseDR extends State(3)  // pause DR shifting
  case object Exit2DR extends State(0)
  case object UpdateDR extends State(5)  // parallel-load output from DR shifter on TCK falling edge while in this state (not a rule?)
  case object SelectIRScan extends State(4)
  case object CaptureIR extends State(14)  // parallel-load IR shifter with fixed logic values and design-specific when exiting this state (if required)
  case object ShiftIR extends State(10)  // shifts IR shifter from TDI towards TDO, last shift occurs on rising edge transition out of this state
  case object Exit1IR extends State(9)
  case object PauseIR extends State(11)  // pause IR shifting
  case object Exit2IR extends State(8)
  case object UpdateIR extends State(13)  // latch IR shifter into IR (changes to IR may only occur while in this state, latch on TCK falling edge)
}

/** The JTAG state machine, implements spec 6.1.1.1a (Figure 6.1)
  *
  * Usage notes:
  * - 6.1.1.1b state transitions occur on TCK rising edge
  * - 6.1.1.1c actions can occur on the following TCK falling or rising edge
  */
class JtagStateMachine extends Module {
  class StateMachineIO extends Bundle {
    val tms = Input(Bool())
    val currState = Output(JtagState.State.chiselType())
  }
  val io = IO(new StateMachineIO)

  // TMS is captured as a single signal, rather than fed directly into the next state logic.
  // This increases the state computation delay at the beginning of a cycle (as opposed to near the
  // end), but theoretically allows a cleaner capture.
  val tms = Reg(Bool(), next=io.tms)  // 4.3.1a captured on TCK rising edge, 6.1.2.1b assumed changes on TCK falling edge

  val nextState = Wire(JtagState.State.chiselType())
  val lastState = Reg(JtagState.State.chiselType(), init=JtagState.TestLogicReset.U, next=nextState)

  io.currState := nextState

  val a = lastState(0)
  val b = lastState(1)
  val c = lastState(2)
  val d = lastState(3)

  val na = !(!(!tms && a && !c) &&
      !(tms && !b) &&
      !(tms && !a) &&
      !(tms && c && d))

  val nb = !(!(!tms && !a && b) &&
      !(!tms && !c) &&
      !(!tms && b && !d) &&
      !(!tms && !a && !d) &&
      !(tms && !b && c) &&
      !(tms && a && c && d))

  val nc = !(!(!b && c) &&
      !(a && c) &&
      !(tms & !b))

  val nd = !(!(!c && d) &&
      !(b && d) &&
      !(!tms && !b && c) &&
      !(!a && !b && c && !d))

  nextState := Cat(nd, nc, nb, na)
}
