// See LICENSE for license details.

package jtag

import chisel3._
import chisel3.util._

/** Base JTAG shifter IO, viewed from input to shift register chain.
  * Can be chained together.
  */
class JtagShifterIO extends Bundle {
  val shift = Input(Bool())  // advance the scan chain on clock high
  val data = Input(Bool())  // as input: bit to be captured into shifter MSB on next rising edge; as output: value of shifter LSB
  val capture = Input(Bool())  // high in the CaptureIR/DR state when this chain is selected
  val update = Input(Bool())  // high in the UpdateIR/DR state when this chain is selected

  /** Sets a output shifter IO's control signals from a input shifter IO's control signals.
    */
  def chainFrom(in: JtagShifterIO) {
    shift := in.shift
    capture := in.capture
    update := in.update
  }
}

trait JtagChainIO extends Bundle {
  val chainIn = new JtagShifterIO
  val chainOut = (new JtagShifterIO).flip()
}

/** Trait that all JTAG chains (data and instruction registers) must extend, providing basic chain
  * IO.
  */
trait JtagChain extends Module {
  val io: JtagChainIO
}

/** One-element shift register, data register for bypass mode.
  *
  * Implements Clause 10.
  */
class JtagBypassChain extends JtagChain {
  class ModIO extends JtagChainIO
  val io = IO(new ModIO)
  io.chainOut chainFrom io.chainIn

  val reg = Reg(Bool())  // 10.1.1a single shift register stage

  io.chainOut.data := reg

  when (io.chainIn.capture) {
    reg := false.B  // 10.1.1b capture logic 0 on TCK rising
  } .elsewhen (io.chainIn.shift) {
    reg := io.chainIn.data
  }
  assert(!(io.chainIn.capture && io.chainIn.update)
      && !(io.chainIn.capture && io.chainIn.shift)
      && !(io.chainIn.update && io.chainIn.shift))
}

/** Simple n-element shift register with parallel capture and update. Useful for general
  * instruction and data scan registers.
  *
  * Useful notes:
  * 7.2.1c shifter shifts on TCK rising edge
  * 4.3.2a TDI captured on TCK rising edge, 6.1.2.1b assumed changes on TCK falling edge
  */
class JtagCaptureUpdateChain(n: Int) extends JtagChain {
  class CaptureIO extends Bundle {
    val bits = Input(UInt(n.W))  // data to capture, should be always valid
    val capture = Output(Bool())  // will be high in capture state (single cycle), captured on following rising edge
  }

  class ModIO extends JtagChainIO {
    val capture = new CaptureIO()
    val update = Output(Valid(UInt(n.W)))  // valid high when in update state (single cycle), contents may change any time after
  }
  val io = IO(new ModIO)
  io.chainOut chainFrom io.chainIn

  val regs = (0 until n) map (x => Reg(Bool()))

  io.chainOut.data := regs(0)
  io.update.bits := Cat(regs.reverse)

  when (io.chainIn.capture) {
    (0 until n) map (x => regs(x) := io.capture.bits(x))
    io.capture.capture := true.B
    io.update.valid := false.B
  } .elsewhen (io.chainIn.update) {
    io.capture.capture := false.B
    io.update.valid := true.B
  } .elsewhen (io.chainIn.shift) {
    regs(n-1) := io.chainIn.data
    (0 until n-1) map (x => regs(x) := regs(x+1))
    io.capture.capture := false.B
    io.update.valid := false.B
  }
  assert(!(io.chainIn.capture && io.chainIn.update)
      && !(io.chainIn.capture && io.chainIn.shift)
      && !(io.chainIn.update && io.chainIn.shift))
}
