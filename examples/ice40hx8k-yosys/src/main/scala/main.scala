package jtagExamples

import chisel3._
import chisel3.util._
import jtag._

class top extends Module {
  class CountIO extends Bundle {
    val count = Output(UInt(32.W))
  }

  class ClockedBlockIO(irLength: Int) extends Bundle {
    val jtag = new JtagIO
    val output = new JtagOutput(irLength)

    val status = Output(Vec(3, Bool()))
  }

  class ModIO extends Bundle {
    val jtag = new JtagIO

    val out = Output(Vec(8, Bool()))
    val out1 = Output(Vec(3, Bool()))
    val out2 = Output(Vec(3, Bool()))

    val state = Output(Vec(4, Bool()))
  }

  val io = IO(new ModIO)
  val irLength = 8

  class JtagTapClocked (modClock: Clock) extends Module(override_clock=Some(modClock)) {
    val tapIo = JtagTapGenerator(irLength, Map(), idcode=Some((1, JtagIdcode(0xA, 0x123, 0x42))))
    val io = IO(tapIo.cloneType)
    io <> tapIo
  }

  // Support for multiple internally-chained JTAG TAPs
  val taps = List(
      Module(new JtagTapClocked(io.jtag.TCK.asClock)),
      Module(new JtagTapClocked(io.jtag.TCK.asClock)),
      Module(new JtagTapClocked(io.jtag.TCK.asClock)),
      Module(new JtagTapClocked(io.jtag.TCK.asClock))
  )
  for (tap <- taps) {
    tap.io.jtag.TCK := io.jtag.TCK
    tap.io.jtag.TMS := io.jtag.TMS
  }
  taps.head.io.jtag.TDI := io.jtag.TDI
  for (prev :: next :: Nil <- taps sliding 2) {
    next.io.jtag.TDI := prev.io.jtag.TDO.data
  }
  io.jtag.TDO := taps.last.io.jtag.TDO

  for (i <- 0 until 8) {
    io.out(i) := false.B
  }
  for (i <- 0 until 3) {
    io.out1(i) := false.B
  }

  val count = ClockedCounter(io.jtag.TCK, 2 ^ 16, 0)  // clock crossing debug counter
  for (i <- 0 until 3) {
    io.out2(i) := count(i)
  }
}

object Top {
  def main(args: Array[String]): Unit = {
    Driver.execute(args, () => new top)
  }
}