// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

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
class JtagOutput(irLength: Int) extends Bundle {
  val instruction = Output(UInt(irLength.W))  // current active instruction
}

/** JTAG block internal status information, for testing purposes.
  */
class JtagStatus(irLength: Int) extends Bundle {
  val state = Output(JtagState.State.chiselType())  // state, transitions on TCK rising edge

}

/** Aggregate JTAG block IO.
  */
class JtagBlockIO(irLength: Int) extends Bundle {
  val jtag = new JtagIO

  val output = new JtagOutput(irLength)

  val status = new JtagStatus(irLength)
  val dataChainOut = Output(new ShifterIO)
  val dataChainIn = Input(new ShifterIO)
}

/** JTAG TAP controller internal block, responsible for instruction decode and data register chain
  * control signal generation.
  *
  * Misc notes:
  * - Figure 6-3 and 6-4 provides examples with timing behavior
  */
class JtagTapController(irLength: Int, initialInstruction: Int) extends Module {
  require(irLength >= 2)  // 7.1.1a

  val io = IO(new JtagBlockIO(irLength))

  val tdo = Wire(Bool())  // 4.4.1c TDI should appear here uninverted after shifting
  val tdo_driven = Wire(Bool())
  io.jtag.TDO.data := NegativeEdgeLatch(clock, tdo)  // 4.5.1a TDO changes on falling edge of TCK or TRST, 6.1.2.1d driver active on first TCK falling edge in ShiftIR and ShiftDR states
  io.jtag.TDO.driven := NegativeEdgeLatch(clock, tdo_driven)

  //
  // JTAG state machine
  //
  val stateMachine = Module(new JtagStateMachine)
  stateMachine.io.tms := io.jtag.TMS
  val currState = stateMachine.io.currState
  io.status.state := stateMachine.io.currState

  //
  // Instruction Register
  //
  // 7.1.1d IR shifter two LSBs must be b01 pattern
  // TODO: 7.1.1d allow design-specific IR bits, 7.1.1e (rec) should be a fixed pattern
  // 7.2.1a behavior of instruction register and shifters
  val irShifter = Module(new CaptureUpdateChain(irLength))
  irShifter.io.chainIn.shift := currState === JtagState.ShiftIR.U
  irShifter.io.chainIn.data := io.jtag.TDI
  irShifter.io.chainIn.capture := currState === JtagState.CaptureIR.U
  irShifter.io.chainIn.update := currState === JtagState.UpdateIR.U
  irShifter.io.capture.bits := "b01".U

  val updateInstruction = Wire(Bool())

  val nextActiveInstruction = Wire(UInt(irLength.W))
  val activeInstruction = NegativeEdgeLatch(clock, nextActiveInstruction, updateInstruction)   // 7.2.1d active instruction output latches on TCK falling edge

  when (currState === JtagState.TestLogicReset.U) {
    // 7.2.1e load IDCODE or BYPASS instruction after entry into TestLogicReset
    nextActiveInstruction := initialInstruction.U(irLength.W)
    updateInstruction := true.B
  } .elsewhen (currState === JtagState.UpdateIR.U) {
    nextActiveInstruction := irShifter.io.update.bits
    updateInstruction := true.B
  } .otherwise {
    updateInstruction := false.B
  }
  io.output.instruction := activeInstruction

  //
  // Data Register
  //
  io.dataChainOut.shift := currState === JtagState.ShiftDR.U
  io.dataChainOut.data := io.jtag.TDI
  io.dataChainOut.capture := currState === JtagState.CaptureDR.U
  io.dataChainOut.update := currState === JtagState.UpdateDR.U

  //
  // Output Control
  //
  when (currState === JtagState.ShiftDR.U) {
    tdo := io.dataChainIn.data
    tdo_driven := true.B
  } .elsewhen (currState === JtagState.ShiftIR.U) {
    tdo := irShifter.io.chainOut.data
    tdo_driven := true.B
  } .otherwise {
    tdo_driven := false.B
  }
}

/** JTAG TAP generator, enclosed module must be clocked from TCK.
  *
  * @param irLength length, in bits, of instruction register, must be at least 2
  *
  * Usage notes:
  * - 4.3.1b TMS must appear high when undriven
  * - 4.3.1c (rec) minimize load presented by TMS
  * - 4.4.1b TDI must appear high when undriven
  * - 4.5.1b TDO must be inactive except when shifting data (undriven? 6.1.2)
  * - 6.1.3.1b TAP controller must not be (re-?)initialized by system reset (allows boundary-scan testing of reset pin)
  *   - 6.1 TAP controller can be initialized by a on-chip power on reset generator, the same one that would initialize system logic
  */
object JtagTapGenerator {
  def apply(irLength: Int, instructions: Map[Chain, Seq[Int]], idcode:Option[(Int, Int)]=None): JtagTapController = {
    val controllerInternal = Module(new JtagTapController(irLength, 0))

    controllerInternal
  }
}
