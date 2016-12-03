// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

/** Bundle representing a tristate pin.
  */
class Tristate extends Bundle {
  val data = Bool()
  val driven = Bool()  // active high, pin is hi-Z when driven is low
}

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
