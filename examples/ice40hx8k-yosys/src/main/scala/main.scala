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
    val state = Output(Vec(4, Bool()))
  }

  val io = IO(new ModIO)
  val irLength = 2

  class JtagTapClocked (modClock: Clock) extends Module(override_clock=Some(modClock)) {
    val io = IO(new ClockedBlockIO(irLength))

    val tap = JtagTapGenerator(irLength, Map(), idcode=Some((1, JtagIdcode(0xA, 0x123, 0x42))))
    io.jtag <> tap.io.jtag
    io.output <> tap.io.output

    io.status(0) := Reg(next=tap.io.output.state === JtagState.TestLogicReset.U)
    io.status(1) := Reg(next=io.jtag.TMS)
    io.status(2) := Reg(next=tap.io.jtag.TDO.driven)
  }
  val tap = Module(new JtagTapClocked(io.jtag.TCK.asClock))
  io.jtag <> tap.io.jtag
  io.out1 <> tap.io.status

  class CounterClocked (modClock: Clock) extends Module(override_clock=Some(modClock)) {
    val io = IO(new CountIO)

    val (cnt, wrap) = Counter(true.B, (1 << 30))
    io.count := cnt
  }
  val count = Module(new CounterClocked(io.jtag.TCK.asClock))

  for (i <- 0 until 8) {
    io.out(i) := count.io.count(i)
  }
//  io.state(0) := tap.io.output.state(0)
//  for (i <- 0 until 4) {
//    io.state(i) := tap.io.output.state(i)
//  }
}

object Top {
  def main(args: Array[String]): Unit = {
    Driver.execute(args, () => new top)
  }
}