package jtagExamples

import chisel3._
import chisel3.util._
import jtag._

class top extends Module {
  class CountIO extends Bundle {
    val count = Output(UInt(32.W))
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

    val chain1 = Module(new CaptureUpdateChain(8))
    chain1.io.capture.bits := reg0
    when (chain1.io.update.valid) {
      reg0 := chain1.io.update.bits
    }

    val chain2 = Module(new CaptureUpdateChain(3))
    chain2.io.capture.bits := reg1
    when (chain2.io.update.valid) {
      reg1 := chain2.io.update.bits
    }

    val chain3 = Module(new CaptureUpdateChain(3))
    chain3.io.capture.bits := reg2
    when (chain3.io.update.valid) {
      reg2 := chain3.io.update.bits
    }

    val chain4 = Module(new CaptureUpdateChain(8))

    val tapIo = JtagTapGenerator(irLength, Map(
          chain1 -> 1,
          chain2 -> 2,
          chain3 -> 3,
          chain4 -> 4
        ),
        idcode=Some((6, JtagIdcode(0xA, 0x123, 0x42))))

    class TapBlockIO(irLength: Int) extends JtagBlockIO(irLength) {
      val reg0 = Output(UInt(8.W))
      val reg1 = Output(UInt(3.W))
      val reg2 = Output(UInt(3.W))

      val queue0 = Output(Decoupled(UInt(8.W)))
    }

    val io = IO(new TapBlockIO(irLength))
    io.jtag <> tapIo.jtag
    io.output <> tapIo.output
    io.reg0 := reg0
    io.reg1 := reg1
    io.reg2 := reg2
    io.queue0.bits := chain4.io.update.bits
    io.queue0.valid := chain4.io.update.valid
  }

  // Generate arbitrary number of chained TAPs
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

  //
  // System logic
  //
  val flip = Reg(Bool(), init=false.B)
  val count = Reg(UInt(8.W), init=0.U)
  // rocket-chip util seem to conflict with chisel3.util
  val resyncQueue0 = _root_.util.AsyncDecoupledFrom(taps.head.clock, taps.head.reset, taps.head.io.queue0)
  resyncQueue0.ready := true.B
  when (resyncQueue0.valid) {
    count := resyncQueue0.bits
    flip := !flip
  }

  //
  // Assign outputs
  //
  io.out0 := Cat(flip, count(6, 0))
  io.out1 := taps.head.io.reg1
  io.out2 := taps.head.io.reg2
}

object Top {
  def main(args: Array[String]): Unit = {
    Driver.execute(args, () => new top)
  }
}