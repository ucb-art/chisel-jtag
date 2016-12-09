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
  val irLength = 4

  class JtagTapClocked (modClock: Clock) extends Module(override_clock=Some(modClock)) {
    val io = IO(new ClockedBlockIO(irLength))

    val tap = JtagTapGenerator(irLength, Map(), idcode=Some((1, JtagIdcode(0xA, 0x123, 0x42))))
    io.jtag <> tap.io.jtag
    io.output <> tap.io.output
  }

  // Support for multiple internally-chained JTAG TAPs
  val taps = List(
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

  // Debug indicator (count cycles using the line itself as a register clock)
  class CounterClocked (modClock: Clock) extends Module(override_clock=Some(modClock)) {
    val io = IO(new CountIO)

    val (cnt, wrap) = Counter(true.B, (1 << 30))
    io.count := cnt
  }
  val count = Module(new CounterClocked(io.jtag.TCK.asClock))

  for (i <- 0 until 8) {
    io.out(i) := false.B
  }

  // Inexplicably necessary, otherwise synthesis breaks
  io.out1(0) := taps(0).io.output.state(0)
  io.out1(1) := taps(1).io.output.state(0)
  io.out1(2) := taps(2).io.output.state(0)

  for (i <- 0 until 3) {
    io.out2(i) := count.io.count(i)
  }
}

object Top {
  def main(args: Array[String]): Unit = {
    Driver.execute(args, () => new top)
  }
}