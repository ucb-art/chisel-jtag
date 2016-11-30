// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

class NegativeEdgeLatch[T <: Data](clock: Clock, dataType: T)
    extends Module(override_clock=Some(clock)) {
  class IoClass extends Bundle {
    val next = Input(dataType)
    val enable = Input(Bool())
    val output = Output(dataType)
  }
  val io = IO(new IoClass)

  val reg = Reg(dataType)
  when (io.enable) {
    reg := io.next
  }
  io.output := reg
}

/** Generates a register that updates on the falling edge of the input clock signal.
  */
object NegativeEdgeLatch {
  def apply[T <: Data](clock: Clock, next: T, enable: Bool=true.B): T = {
    // TODO better init passing once in-module multiclock support improves
    val latch_module = Module(new NegativeEdgeLatch((!clock.asUInt).asClock, next.cloneType))
    latch_module.io.next := next
    latch_module.io.enable := enable
    latch_module.io.output
  }
}

object ParallelShiftRegister {
  /** Generates a shift register with a parallel output (all bits simultaneously visible), parallel
    * input (load), a one-bit input (into the first element), and a shift enable signal.
    *
    * The input is shifted into the most significant bits.
    *
    * @param n bits in shift register
    * @param input single bit input, when shift is high, this is loaded into the first element
    * @param shift shift enable control
    * @param load parallel load control
    * @param loadData parallel load data
    */
  def apply(n: Int, shift: Bool, input: Bool, load: Bool, loadData: UInt): UInt = {
    val regs = (0 until n) map (x => Reg(Bool()))
    when (shift) {
      regs(0) := input
      (1 until n) map (x => regs(x) := regs(x-1))
    } .elsewhen (load) {
      (0 until n) map (x => regs(x) := loadData(x))
    }
    Cat(regs)
  }
}

/** Bundle representing a tristate pin.
  */
class Tristate extends Bundle {
  val data = Bool()
  val driven = Bool()  // active high, pin is hi-Z when driven is low
}