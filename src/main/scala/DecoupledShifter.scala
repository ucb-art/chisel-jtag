// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

/** JTAG shifter chain that acts as a source for an internal Decoupled. gen is the type of the
  * Decoupled, without the Decoupled. The output Decoupled must be on the same clock as the JTAG
  * TAP and must never revoke a ready status (otherwise, add a queue).
  *
  * The scan chain input consists of a valid bit (MSbit) followed by the bits of gen (LSbits).
  * The scan chain output consists of just the ready bit.
  *
  * During the Capture state, the ready bit is latched in both the scan chain and to an internal
  * register.
  * During the Update state, the scanned-in valid bit is gated with the registered ready bit
  * (above) and made available to the external interface for one cycle. This allows flow control to
  * happen within a single JTAG capture/update cycle.
  *
  * The alternative would have been to make the previous ready available on the next JTAG capture,
  * but this would require pipelined transfers and more complicated driving software.
  */
class DecoupledSourceChain[+T <: Data](gen: T) extends Chain {
  class ModIO extends ChainIO {
    val interface = Decoupled(gen)
  }
  val io = IO(new ModIO)

  class UpdateType extends Bundle {
    val valid = Bool()  // MSbit
    val bits = gen.chiselCloneType  // LSbits
    override def cloneType: this.type = (new UpdateType).asInstanceOf[this.type]
  }

  val chain = Module(CaptureUpdateChain(Bool(), new UpdateType))
  chain.io.chainIn := io.chainIn
  io.chainOut := chain.io.chainOut

  chain.io.capture.bits := io.interface.ready

  val readyReg = Reg(Bool())
  when (chain.io.capture.capture) {
    readyReg := io.interface.ready
  }

  io.interface.bits := chain.io.update.bits.bits
  when (chain.io.update.valid) {
    io.interface.valid := readyReg && chain.io.update.bits.valid
    assert(io.interface.ready === readyReg)
  } .otherwise {
    io.interface.valid := false.B
  }
}

/** JTAG shifter chain that acts as a sink for an internal Decoupled. gen is the type of the
  * Decoupled, without the Decoupled. The input Decoupled must be on the same clock as the JTAG
  * TAP and must never revoke valid or change data once valid (otherwise, add a queue).
  *
  * The scan chain input consists of just the ready bit.
  * The scan chain output consists of a valid bit (MSbit) followed by the bits of gen (LSbits).
  *
  * During the Capture state, the valid bit and data from the external interface are latched into
  * the scan chain.
  * During the Update state, the scanned-in ready bit is gated with the registered valid bit
  */
class DecoupledSinkChain[+T <: Data](gen: T) extends Chain {
  class ModIO extends ChainIO {
    val interface = Flipped(Decoupled(gen))
  }
  val io = IO(new ModIO)

  class CaptureType extends Bundle {
    val valid = Bool()  // MSbit
    val bits = gen.chiselCloneType  // LSbits
    override def cloneType: this.type = (new CaptureType).asInstanceOf[this.type]
  }

  val chain = Module(CaptureUpdateChain(new CaptureType, Bool()))
  chain.io.chainIn := io.chainIn
  io.chainOut := chain.io.chainOut

  chain.io.capture.bits.valid := io.interface.valid
  chain.io.capture.bits.bits := io.interface.bits
  
  val validReg = Reg(Bool())
  when (chain.io.capture.capture) {
    validReg := io.interface.valid
  }
  
  when (chain.io.update.valid) {
    io.interface.ready := validReg && chain.io.update.bits
    assert(io.interface.valid === validReg)
  } .otherwise {
    io.interface.ready := false.B
  }
}
