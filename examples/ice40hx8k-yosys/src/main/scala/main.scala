package jtagExamples

import chisel3._
import chisel3.util._
import jtag._

class Top extends Module {
  class ModIO extends Bundle {
    val jtag = new JtagIO

    val out0 = Output(UInt(8.W))
    val out1 = Output(UInt(3.W))
    val out2 = Output(UInt(3.W))
  }

  val io = IO(new ModIO)
  val irLength = 4

  //
  // TAP blocks
  //
  class JtagTapClocked(modClock: Clock, modReset: Bool)
      extends Module(override_clock=Some(modClock), override_reset=Some(modReset)) {
    val chain0 = Module(CaptureUpdateChain(UInt(8.W)))
    val reg0 = RegEnable(chain0.io.update.bits, 0.U, chain0.io.update.valid)
    chain0.io.capture.bits := reg0

    val chain1 = Module(CaptureUpdateChain(UInt(3.W)))
    val reg1 = RegEnable(chain1.io.update.bits, 1.U, chain1.io.update.valid)
    chain1.io.capture.bits := reg1

    val chain2 = Module(CaptureUpdateChain(UInt(3.W)))
    val reg2 = RegEnable(chain2.io.update.bits, 0.U, chain2.io.update.valid)
    chain2.io.capture.bits := reg2

    val tapIo = JtagTapGenerator(irLength, Map(
          chain0 -> 1,
          chain1 -> 2,
          chain2 -> 3
        ),
        idcode=Some((14, JtagIdcode(0xA, 0x123, 0x42))))

    class TapBlockIO(irLength: Int) extends JtagBlockIO(irLength) {
      val reg0 = Output(UInt(8.W))
      val reg1 = Output(UInt(3.W))
      val reg2 = Output(UInt(3.W))
    }

    val io = IO(new TapBlockIO(irLength))
    io.jtag <> tapIo.jtag
    io.output <> tapIo.output

    io.reg0 := reg0
    io.reg1 := reg1
    io.reg2 := reg2
  }

  // Generate arbitrary number of chained TAPs
  val tap_reset = Wire(Bool())
  val tap = Module(new JtagTapClocked(io.jtag.TCK.asClock, tap_reset))
  tap_reset := tap.io.output.reset
  tap.io.jtag.TCK := io.jtag.TCK
  tap.io.jtag.TMS := io.jtag.TMS
  tap.io.jtag.TDI := io.jtag.TDI
  io.jtag.TDO := tap.io.jtag.TDO
  tap.io.control.fsmAsyncReset := false.B

  //
  // Assign outputs
  //
  io.out0 := tap.io.reg0
  io.out1 := tap.io.reg1
  io.out2 := tap.io.reg2
}

object Top {
  def main(args: Array[String]): Unit = {
    Driver.execute(args, () => new Top)
  }
}