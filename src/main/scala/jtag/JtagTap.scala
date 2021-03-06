// See LICENSE for license details.

package jtag

import chisel3._

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
  val state = Output(JtagState.State.chiselType())  // state, transitions on TCK rising edge
  val instruction = Output(UInt(irLength.W))  // current active instruction
  val reset = Output(Bool())  // synchronous reset asserted in Test-Logic-Reset state, should NOT hold the FSM in reset

  override def cloneType = new JtagOutput(irLength).asInstanceOf[this.type]
}

class JtagControl extends Bundle {
  val fsmAsyncReset = Input(Bool())  // TODO: asynchronous reset for FSM, used for TAP_POR*
}

/** Aggregate JTAG block IO.
  */
class JtagBlockIO(val irLength: Int) extends Bundle {
  val jtag = new JtagIO
  val control = new JtagControl
  val output = new JtagOutput(irLength)
}

/** Internal controller block IO with data shift outputs.
  */
class JtagControllerIO(irLength: Int) extends JtagBlockIO(irLength) {
  val dataChainOut = Output(new ShifterIO)
  val dataChainIn = Input(new ShifterIO)
}

/** JTAG TAP controller internal block, responsible for instruction decode and data register chain
  * control signal generation.
  *
  * Misc notes:
  * - Figure 6-3 and 6-4 provides examples with timing behavior
  */
class JtagTapController(irLength: Int, initialInstruction: BigInt) extends Module {
  require(irLength >= 2)  // 7.1.1a

  val io = IO(new JtagControllerIO(irLength))

  val tdo = Wire(Bool())  // 4.4.1c TDI should appear here uninverted after shifting
  tdo := DontCare  //TODO: figure out what isn't getting connected
  val tdo_driven = Wire(Bool())
  tdo_driven := DontCare  //TODO: figure out what isn't getting connected
  io.jtag.TDO.data := NegativeEdgeLatch(clock, tdo)  // 4.5.1a TDO changes on falling edge of TCK or TRST, 6.1.2.1d driver active on first TCK falling edge in ShiftIR and ShiftDR states
  io.jtag.TDO.driven := NegativeEdgeLatch(clock, tdo_driven)


  //
  // JTAG state machine
  //
  val stateMachine = Module(new JtagStateMachine)
  stateMachine.io.tms := io.jtag.TMS
  val currState = stateMachine.io.currState
  io.output.state := stateMachine.io.currState
  stateMachine.io.asyncReset := io.control.fsmAsyncReset

  //
  // Instruction Register
  //
  // 7.1.1d IR shifter two LSBs must be b01 pattern
  // TODO: 7.1.1d allow design-specific IR bits, 7.1.1e (rec) should be a fixed pattern
  // 7.2.1a behavior of instruction register and shifters
  val irShifter = Module(CaptureUpdateChain(UInt(irLength.W)))
  irShifter.io.chainIn.shift := currState === JtagState.ShiftIR.U
  irShifter.io.chainIn.data := io.jtag.TDI
  irShifter.io.chainIn.capture := currState === JtagState.CaptureIR.U
  irShifter.io.chainIn.update := currState === JtagState.UpdateIR.U
  irShifter.io.capture.bits := "b01".U

  val updateInstruction = Wire(Bool())
  updateInstruction := DontCare  //TODO: figure out what isn't getting connected

  val nextActiveInstruction = Wire(UInt(irLength.W))
  nextActiveInstruction := DontCare  //TODO: figure out what isn't getting connected

  val activeInstruction = NegativeEdgeLatch(clock, nextActiveInstruction, updateInstruction)   // 7.2.1d active instruction output latches on TCK falling edge

  when (reset.asBool) {
    nextActiveInstruction := initialInstruction.U(irLength.W)
    updateInstruction := true.B
  } .elsewhen (currState === JtagState.UpdateIR.U) {
    nextActiveInstruction := irShifter.io.update.bits
    updateInstruction := true.B
  } .otherwise {
    updateInstruction := false.B
  }
  io.output.instruction := activeInstruction

  io.output.reset := currState === JtagState.TestLogicReset.U

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

object JtagTapGenerator {
  /** JTAG TAP generator, enclosed module must be clocked from TCK and reset from output of this
    * block.
    *
    * @param irLength length, in bits, of instruction register, must be at least 2
    * @param instructions map of instruction codes to data register chains that select that data
    * register; multiple instructions may map to the same data chain
    * @param idcode optional idcode, tuple of (instruction code, idcode)
    *
    * @note all other instruction codes (not part of instructions or idcode) map to BYPASS
    * @note initial instruction is idcode (if supported), otherwise all ones BYPASS
    *
    * Usage notes:
    * - 4.3.1b TMS must appear high when undriven
    * - 4.3.1c (rec) minimize load presented by TMS
    * - 4.4.1b TDI must appear high when undriven
    * - 4.5.1b TDO must be inactive except when shifting data (undriven? 6.1.2)
    * - 6.1.3.1b TAP controller must not be (re-?)initialized by system reset (allows
    *   boundary-scan testing of reset pin)
    *   - 6.1 TAP controller can be initialized by a on-chip power on reset generator, the same one
    *     that would initialize system logic
    *
    * TODO:
    * - support concatenated scan chains
    */
  def apply(irLength: Int, instructions: Map[BigInt, Chain], idcode:Option[(BigInt, BigInt)]=None): JtagBlockIO = {
    // Create IDCODE chain if needed
    val allInstructions = idcode match {
      case Some((icode, idcode)) => {
        val idcodeModule = Module(CaptureChain(UInt(32.W)))
        require(idcode % 2 == 1, "LSB must be set in IDCODE, see 12.1.1d")
        require(((idcode >> 1) & ((1 << 11) - 1)) != JtagIdcode.dummyMfrId, "IDCODE must not have 0b00001111111 as manufacturer identity, see 12.2.1b")
        require(!(instructions contains icode), "instructions may not contain IDCODE")
        idcodeModule.io.capture.bits := idcode.U(32.W)
        instructions + (icode -> idcodeModule)
      }
      case None => instructions
    }

    val bypassIcode = (BigInt(1) << irLength) - 1  // required BYPASS instruction
    val initialInstruction = idcode match {  // 7.2.1e load IDCODE or BYPASS instruction after entry into TestLogicReset
      case Some((icode, _)) => icode
      case None => bypassIcode
    }

    require(!(allInstructions contains bypassIcode), "instructions may not contain BYPASS code")

    val controllerInternal = Module(new JtagTapController(irLength, initialInstruction))

    val unusedChainOut = Wire(new ShifterIO)  // De-selected chain output
    unusedChainOut.shift := false.B
    unusedChainOut.data := false.B
    unusedChainOut.capture := false.B
    unusedChainOut.update := false.B

    val bypassChain = Module(JtagBypassChain())

    // The Great Data Register Chain Mux
    bypassChain.io.chainIn := controllerInternal.io.dataChainOut  // for simplicity, doesn't visibly affect anything else
    require(allInstructions.size > 0, "Seriously? JTAG TAP with no instructions?")

    val chainToIcode = allInstructions groupBy { case (icode, chain) => chain } map {
      case (chain, icodeToChain) => chain -> icodeToChain.keys
    }

    val chainToSelect = chainToIcode map {
      case (chain, icodes) => {
        assume(icodes.size > 0)
        val icodeSelects = icodes map { controllerInternal.io.output.instruction === _.asUInt(irLength.W) }
        chain -> icodeSelects.reduceLeft(_||_)
      }
    }

    def foldOutSelect(res: WhenContext, x: (Chain, Bool)): WhenContext = {
      val (chain, select) = x
      // Continue the WhenContext with if this chain is selected
      res.elsewhen(select) {
        controllerInternal.io.dataChainIn := chain.io.chainOut
      }
    }

    controllerInternal.io.dataChainIn := DontCare  //TODO: figure out what isn't getting connected
    val emptyWhen = when (false.B) { }  // Empty WhenContext to start things off
    chainToSelect.toSeq.foldLeft(emptyWhen)(foldOutSelect).otherwise {
      controllerInternal.io.dataChainIn := bypassChain.io.chainOut
    }

    def mapInSelect(x: (Chain, Bool)) {
      val (chain, select) = x
      when (select) {
        chain.io.chainIn := controllerInternal.io.dataChainOut
      } .otherwise {
        chain.io.chainIn := unusedChainOut
      }
    }

    chainToSelect.map(mapInSelect)

    val internalIo = Wire(new JtagBlockIO(irLength))
    internalIo := DontCare  //TODO: figure out what isn't getting connected

    controllerInternal.io.jtag <> internalIo.jtag
    controllerInternal.io.control <> internalIo.control
    controllerInternal.io.output <> internalIo.output

    internalIo
  }
}
