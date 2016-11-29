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

object NegativeEdgeLatch {
  def apply(clock: Bool, signal: Data, width: Int): UInt = {
    val latch_module = Module(new NegativeEdgeLatch((!clock).asClock, width))
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

class JtagTapInternal(clock: Clock) extends Module(override_clock=Some(clock)) {
  val io = IO(new JtagIO)

  // Signals captured on negative edge
  val tms = NegativeEdgeLatch(io.TCK, io.TMS, 1)
  val tdi = NegativeEdgeLatch(io.TCK, io.TDI, 1)
  val tdo = Reg(Bool())
  tdo := tdi
  io.TDO := tdo
}

class JtagTap() extends Module {
  val io = IO(new JtagIO)
  
  val tap = Module(new JtagTapInternal(io.TCK.asClock))
  io <> tap.io
}
