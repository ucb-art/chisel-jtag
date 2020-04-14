// See LICENSE for license details.

package jtag

import org.scalatest._
import chisel3.iotesters.experimental.VerilatorBackend
import chisel3._
import jtag._

class ModuleIO(override val irLength: Int) extends JtagBlockIO(irLength) {
  val reg = Output(UInt(8.W))
}

class JtagTapSpec extends FlatSpec with JtagTestUtilities {
  "JTAG TAP" should "output a proper IDCODE" in {
    test(JtagClocked("idcode", () => new JtagModule {
      val controller = JtagTapGenerator(2, Map(), idcode=Some((0, JtagIdcode(0xA, 0x123, 0x42))))
      val io = IO(chiselTypeOf(controller))
      io <> controller
    }), testerBackend=VerilatorBackend) { implicit t => c =>
      import BinaryParse._

      resetToIdle(c.io)
      idleToIRShift(c.io)
      irShift(c.io, "00", "10")
      irShiftToIdle(c.io)

      idleToDRShift(c.io)
      drShift(c.io, "00000000000000000000000000000000", "1010 0000000100100011 00001000010 1".reverse)
      drShiftToIdle(c.io)
    }
  }

  "JTAG data registers" should "capture and update" in {
    test(JtagClocked("regCaptureUpdate", () => new JtagModule {
      val irLength = 4

      val reg = RegInit(42.U(8.W))

      val chain = Module(CaptureUpdateChain(UInt(8.W)))
      chain.io.capture.bits := reg
      when (chain.io.update.valid) {
        reg := chain.io.update.bits
      }

      val controller = JtagTapGenerator(irLength, Map(1 -> chain))


      val io = IO(new ModuleIO(irLength))
      io.jtag <> controller.jtag
      io.output <> controller.output
      io.reg := reg
    }), testerBackend=VerilatorBackend) { implicit t => c =>
      import BinaryParse._

      resetToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 42)  // reset sanity check
      idleToIRShift(c.io)
      irShift(c.io, "0001".reverse, "??01".reverse)
      irShiftToIdle(c.io)

      idleToDRShift(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 42)
      drShift(c.io, "00101111", "00101010".reverse)
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0xF4)
      idleToDRShift(c.io)
      drShift(c.io, "11111111", "00101111")
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0xFF)
      idleToDRShift(c.io)
      drShift(c.io, "00000000", "11111111")
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0x00)
    }
  }

  "JTAG TAP" should "allow multiple instructions to select the same chain for scan" in {
    test(JtagClocked("multipleIcodes", () => new JtagModule {
      val irLength = 4

      val reg = RegInit(42.U(8.W))

      val chain = Module(CaptureUpdateChain(UInt(8.W)))
      chain.io.capture.bits := reg
      when (chain.io.update.valid) {
        reg := chain.io.update.bits
      }

      val controller = JtagTapGenerator(irLength, Map(1 -> chain, 2-> chain))

      val io = IO(new ModuleIO(irLength))
      io.jtag <> controller.jtag
      io.output <> controller.output
      io.reg := reg
    }), testerBackend=VerilatorBackend) { implicit t => c =>
      import BinaryParse._

      resetToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 42)  // reset sanity check
      idleToIRShift(c.io)
      irShift(c.io, "0001".reverse, "??01".reverse)
      irShiftToIdle(c.io)

      idleToDRShift(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 42)
      drShift(c.io, "00101111", "00101010".reverse)
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0xF4)
      idleToDRShift(c.io)
      drShift(c.io, "11111111", "00101111")
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0xFF)
      idleToDRShift(c.io)
      drShift(c.io, "00000000", "11111111")
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0x00)

      idleToIRShift(c.io)
      irShift(c.io, "0010".reverse, "??01".reverse)
      irShiftToIdle(c.io)

      idleToDRShift(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0x00)
      drShift(c.io, "00101111", "00000000".reverse)
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0xF4)
      idleToDRShift(c.io)
      drShift(c.io, "11111111", "00101111")
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0xFF)
      idleToDRShift(c.io)
      drShift(c.io, "00000000", "11111111")
      drShiftToIdle(c.io)
      check(c.io.asInstanceOf[ModuleIO].reg, 0x00)
    }
  }
}
