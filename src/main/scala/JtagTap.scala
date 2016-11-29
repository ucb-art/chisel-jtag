// See LICENSE for license details.

package jtag

import chisel3._

class NegativeEdgeLatch(clock: Clock, width: Int) extends Module(override_clock=Some(clock)) {
  class IoClass extends Bundle {
    val input = Input(UInt(width.W))
    val output = Output(UInt(width.W))
  }
  val io = IO(new IoClass)

  val reg = Reg(UInt(width.W))
  reg := io.input
  io.output := reg
}

/** Generates a register that updates on the falling edge of the input clock signal.
  */
object NegativeEdgeLatch {
  def apply(clock: Clock, signal: Data, width: Int): UInt = {
    val latch_module = Module(new NegativeEdgeLatch((!clock.asUInt).asClock, width))
    latch_module.io.input := signal
    latch_module.io.output
  }
}

/** JTAG signals, viewed from the device side.
  */
class JtagIO extends Bundle {
  // TRST is optional and not currently implemented.
  val TCK = Input(Bool())
  val TMS = Input(Bool())
  val TDI = Input(Bool())
  val TDO = Output(Bool())
}

/** JTAG TAP internal block, that has a overridden clock so registers can be clocked on TCK rising.
  */
class JtagTapInternal(mod_clock: Clock) extends Module(override_clock=Some(mod_clock)) {
  val io = IO(new JtagIO)

  // Signals captured on negative edge
  val tms = NegativeEdgeLatch(clock, io.TMS, 1)
  val tdi = NegativeEdgeLatch(clock, io.TDI, 1)
  val tdo = Reg(Bool())
  tdo := tdi
  io.TDO := tdo
}

class JtagTap() extends Module {
  val io = IO(new JtagIO)

  val tap = Module(new JtagTapInternal(io.TCK.asClock))
  io <> tap.io
}
