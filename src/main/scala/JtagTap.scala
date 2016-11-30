// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

class NegativeEdgeLatch[T <: Data](clock: Clock, dataType: T)
    extends Module(override_clock=Some(clock)) {
  class IoClass extends Bundle {
    val next = Input(dataType)
    val enable = Input(Bool())
    val output = Output(dataType)
  }
  val io = IO(new IoClass)

  val reg = Reg(dataType)
  when (io.enable) {
    reg := io.next
  }
  io.output := reg
}

/** Generates a register that updates on the falling edge of the input clock signal.
  */
object NegativeEdgeLatch {
  def apply[T <: Data](clock: Clock, next: T, enable: Bool=true.B): T = {
    // TODO better init passing once in-module multiclock support improves
    val latch_module = Module(new NegativeEdgeLatch((!clock.asUInt).asClock, next.cloneType))
    latch_module.io.next := next
    latch_module.io.enable := enable
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

class Tristate extends Bundle {
  val data = Bool()
  val driven = Bool()  // active high, pin is hi-Z when driven is low
}

/** JTAG signals, viewed from the device side.
  */
class JtagIO extends Bundle {
  // TRST (4.6) is optional and not currently implemented.
  val TCK = Input(Bool())
  val TMS = Input(Bool())
  val TDI = Input(Bool())
  val TDO = Output(new Tristate())
}

/** JTAG block output signals.
  */
class JtagOutput(numInstructions: Int) extends Bundle {
  // A Vec of Bools, with the high one indicating the active instruction
  // If multiple instructions mapped to the same IR code, they may be simultaneously high
  // Instruction 0 is high on reset
  // Updated on TCK falling edge, registered to be glitch-free (as recommended by 7.2.2)
  val instruction = Output(Vec(numInstructions, Bool()))
}

/** JTAG block internal status information, for testing purposes.
  */
class JtagStatus(irLength: Int) extends Bundle {
  val state = Output(JtagState.State.chiselType())  // state, transitions on TCK rising edge
  val instruction = Output(UInt(irLength.W))  // current active instruction
}

/** Aggregate JTAG block IO
  */
class JtagBlockIO(irLength: Int, numInstructions: Int) extends Bundle {
  val jtag = new JtagIO
  val output = new JtagOutput(numInstructions)

  val status = new JtagStatus(irLength)
}

object ParallelShiftRegister {
  /** Generates a shift register with a parallel output (all bits simultaneously visible), parallel
    * input (load), a one-bit input (into the first element), and a shift enable signal.
    *
    * The input is shifted into the most significant bits.
    *
    * @param n bits in shift register
    * @param input single bit input, when shift is high, this is loaded into the first element
    * @param shift shift enable control
    * @param load parallel load control
    * @param loadData parallel load data
    */
  def apply(n: Int, shift: Bool, input: Bool, load: Bool, loadData: UInt): UInt = {
    val regs = (0 until n) map (x => Reg(Bool()))
    when (shift) {
      regs(0) := input
      (1 until n) map (x => regs(x) := regs(x-1))
    } .elsewhen (load) {
      (0 until n) map (x => regs(x) := loadData(x))
    }
    Cat(regs)
  }
}

/** JTAG TAP internal block, that has a overridden clock so registers can be clocked on TCK rising.
  *
  * Misc notes:
  * - Figure 6-3 and 6-4 provides examples with timing behavior
  *
  * TODO:
  * - Implement test mode persistence (TMP) controller, 6.2
  */
class JtagTapInternal(mod_clock: Clock, irLength: Int, instructions: Map[UInt, Int])
    extends Module(override_clock=Some(mod_clock)) {
  require(irLength >= 2)  // 7.1.1a
  val numInstructions = instructions.valuesIterator.max + 1
  require(numInstructions >= 1)

  val io = IO(new JtagBlockIO(irLength, numInstructions))

  val tms = Reg(Bool(), next=io.jtag.TMS)  // 4.3.1a captured on TCK rising edge, 6.1.2.1b assumed changes on TCK falling edge
  val tdi = Reg(Bool(), next=io.jtag.TDI)  // 4.3.2a captured on TCK rising edge, 6.1.2.1b assumed changes on TCK falling edge
  val tdo = Wire(Bool())  // 4.4.1c TDI should appear here uninverted after shifting
  val tdo_driven = Wire(Bool())
  io.jtag.TDO.data := NegativeEdgeLatch(clock, tdo)  // 4.5.1a TDO changes on falling edge of TCK or TRST, 6.1.2.1d driver active on first TCK falling edge in ShiftIR and ShiftDR states
  io.jtag.TDO.driven := NegativeEdgeLatch(clock, tdo_driven)

  //
  // JTAG state machine
  //
  val stateMachine = Module(new JtagStateMachine)
  stateMachine.io.TMS := tms
  val currState = stateMachine.io.currState

  //
  // Shift Registers
  //
  // 7.1.1d IR shifter two LSBs must be b01 pattern
  // TODO: 7.1.1d allow design-specific IR bits, 7.1.1e (rec) should be a fixed pattern
  // 7.2.1a behavior of instruction register and shifters
  // 7.2.1c shifter shifts on TCK rising edge
  val irShifter = ParallelShiftRegister(irLength, currState === JtagState.ShiftIR.U, tdi,
      currState === JtagState.CaptureIR.U, "b01".U)
  val updateInstruction = Wire(Bool())

  val nextActiveInstruction = Wire(UInt(irLength.W))
  val activeInstruction = NegativeEdgeLatch(clock, nextActiveInstruction, updateInstruction)   // 7.2.1d active instruction output latches on TCK falling edge
  val nextDecodedInstruction = Wire(Vec(numInstructions, Bool()))
  val decodedInstruction = NegativeEdgeLatch(clock, nextDecodedInstruction, updateInstruction)

  when (currState === JtagState.TestLogicReset.U) {
    // 7.2.1e load IDCODE or BYPASS instruction after entry into TestLogicReset
    nextActiveInstruction := 0.U // TODO IDCODE or BYPASS
    nextDecodedInstruction(0) := true.B
    (1 until numInstructions) map (x => nextDecodedInstruction(x) := false.B)
    updateInstruction := true.B
  } .elsewhen (currState === JtagState.UpdateIR.U) {
    nextActiveInstruction := irShifter
    (0 until numInstructions) map { x =>
      val icodes = instructions.toSeq.  // to tuples of (instr code, bitvector)
        filter(_._2 == x).  // only where bitvector is position under consideration
        map(_._1 === irShifter)  // to list of instr codes === curr instruction
      nextDecodedInstruction(x) := icodes.fold(false.B)(_ || _)
    }
    updateInstruction := true.B
  } .otherwise {
    updateInstruction := false.B
  }
  io.status.instruction := activeInstruction

  //
  // Output Control
  //
  when (currState === JtagState.ShiftDR.U) {
    // TODO: DR shift
    tdo_driven := true.B
  } .elsewhen (currState === JtagState.ShiftIR.U) {
    tdo := irShifter(0)
    tdo_driven := true.B
  } .otherwise {
    tdo_driven := false.B
  }

  // Notes
  // IR should be initialized to IDCODE instruction (when available) or BYPASS
}

/** JTAG TAP block, clocked from TCK.
  *
  * @param irLength length, in bits, of instruction register, must be at least 2
  * @param instruction a map of instruction code literal to output bitvector position representing
  * active instruction; it is permissible to have multiple instruction codes map to the same position
  *
  * Usage notes:
  * - 4.3.1b TMS must appear high when undriven
  * - 4.3.1c (rec) minimize load presented by TMS
  * - 4.4.1b TDI must appear high when undriven
  * - 4.5.1b TDO must be inactive except when shifting data (undriven? 6.1.2)
  * - 6.1.3.1b TAP controller must not be (re-?)initialized by system reset (allows boundary-scan testing of reset pin)
  *   - 6.1 TAP controller can be initialized by a on-chip power on reset generator, the same one that would initialize system logic
  */
class JtagTap(irLength: Int, instructions: Map[UInt, Int]) extends Module {
  val io = IO(new JtagBlockIO(irLength, instructions.valuesIterator.max + 1))

  val tap = Module(new JtagTapInternal(io.jtag.TCK.asClock, irLength, instructions))
  io <> tap.io
}
