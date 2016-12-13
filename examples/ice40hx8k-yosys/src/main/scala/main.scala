package jtagExamples

import chisel3._
import chisel3.util._
import jtag._

class top extends Module {
  class CountIO extends Bundle {
    val count = Output(UInt(32.W))
  }

  class ClockedBlockIO(irLength: Int) extends JtagBlockIO(irLength) {
    val reg0 = Output(UInt(8.W))
    val reg1 = Output(UInt(3.W))
    val reg2 = Output(UInt(3.W))
  }

  class ModIO extends Bundle {
    val jtag = new JtagIO

    val out0 = Output(UInt(8.W))
    val out1 = Output(UInt(3.W))
    val out2 = Output(UInt(3.W))
  }

  val io = IO(new ModIO)
  val irLength = 3

  class JtagTapClocked (modClock: Clock, modReset: Bool)
      extends Module(override_clock=Some(modClock), override_reset=Some(modReset)) {
    val reg0 = Reg(UInt(8.W), init=0x55.U)
    val reg1 = Reg(UInt(3.W), init=5.U)
    val reg2 = Reg(UInt(3.W), init=5.U)

    val chain0 = Module(new CaptureUpdateChain(8))
    chain0.io.capture.bits := reg0
    when (chain0.io.update.valid) {
      reg0 := chain0.io.update.bits
    }

    val chain1 = Module(new CaptureUpdateChain(3))
    chain1.io.capture.bits := reg1
    when (chain1.io.update.valid) {
      reg1 := chain1.io.update.bits
    }

    val chain2 = Module(new CaptureUpdateChain(3))
    chain2.io.capture.bits := reg2
    when (chain2.io.update.valid) {
      reg2 := chain2.io.update.bits
    }

    val tapIo = JtagTapGenerator(irLength, Map(
          chain0 -> 1,
          chain1 -> 2,
          chain2 -> 3
        ),
        idcode=Some((6, JtagIdcode(0xA, 0x123, 0x42))))
    val io = IO(new ClockedBlockIO(irLength))
    io.jtag <> tapIo.jtag
    io.output <> tapIo.output
    io.reg0 := reg0
    io.reg1 := reg1
    io.reg2 := reg2
  }

  // Support for multiple internally-chained JTAG TAPs
  val tap_reset = Wire(Bool())
  val taps = List(
      Module(new JtagTapClocked(io.jtag.TCK.asClock, tap_reset))
  )
  tap_reset := taps.map(_.io.output.reset).fold(false.B)(_||_)
  for (tap <- taps) {
    tap.io.jtag.TCK := io.jtag.TCK
    tap.io.jtag.TMS := io.jtag.TMS
    tap.io.control.fsmAsyncReset := false.B
  }
  taps.head.io.jtag.TDI := io.jtag.TDI
  for (List(prev, next) <- taps sliding 2) {
    next.io.jtag.TDI := prev.io.jtag.TDO.data
  }
  io.jtag.TDO := taps.last.io.jtag.TDO

  io.out0 := taps.head.io.reg0
  io.out1 := taps.head.io.reg1
  io.out2 := taps.head.io.reg2
}

object Top {
  def main(args: Array[String]): Unit = {
    Driver.execute(args, () => new top)
  }
}