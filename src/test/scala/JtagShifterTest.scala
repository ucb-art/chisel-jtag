// See LICENSE for license details.

package jtag.test

import org.scalatest._

import chisel3.iotesters.experimental.ImplicitPokeTester

import chisel3._
import jtag._

trait ChainIOUtils extends ImplicitPokeTester {
  // CaptureUpdateChain test utilities
  def nop(io: ChainIO)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 0)
    poke(io.chainIn.capture, 0)
    poke(io.chainIn.update, 0)

    check(io.chainOut.shift, 0)
    check(io.chainOut.capture, 0)
    check(io.chainOut.update, 0)
  }

  def capture(io: ChainIO)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 0)
    poke(io.chainIn.capture, 1)
    poke(io.chainIn.update, 0)

    check(io.chainOut.shift, 0)
    check(io.chainOut.capture, 1)
    check(io.chainOut.update, 0)
  }

  def shift(io: ChainIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 1)
    poke(io.chainIn.capture, 0)
    poke(io.chainIn.update, 0)

    check(io.chainOut.shift, 1)
    check(io.chainOut.capture, 0)
    check(io.chainOut.update, 0)

    check(io.chainOut.data, expectedDataOut)
    poke(io.chainIn.data, dataIn)
  }

  def update(io: ChainIO)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 0)
    poke(io.chainIn.capture, 0)
    poke(io.chainIn.update, 1)

    check(io.chainOut.shift, 0)
    check(io.chainOut.capture, 0)
    check(io.chainOut.update, 1)

  }

  // CaptureChain test utilities
  def nop(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    nop(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
  }

  def capture(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    capture(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 1)
  }

  def shift(io: CaptureChain[Data]#ModIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    shift(io.asInstanceOf[ChainIO], expectedDataOut, dataIn)
    check(io.capture.capture, 0)
  }

  def update(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    update(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
  }

  // CaptureUpdateChain test utilities
  def nop(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    nop(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
    check(io.update.valid, 0)
  }

  def capture(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    capture(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 1)
    check(io.update.valid, 0)
  }

  def shift(io: CaptureUpdateChain[Data, Data]#ModIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    shift(io.asInstanceOf[ChainIO], expectedDataOut, dataIn)
    check(io.capture.capture, 0)
    check(io.update.valid, 0)
  }

  def update(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    update(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
    check(io.update.valid, 1)
  }
}

class JtagShifterSpec extends FlatSpec with ChainIOUtils {
  import BinaryParse._

  "JTAG bypass chain" should "work" in {
    test(JtagBypassChain()) { implicit t => c =>
      nop(c.io)
      step()

      // Shift without expect
      poke(c.io.chainIn.shift, 1)
      poke(c.io.chainIn.data, 0)
      step()

      // Test shift functionality
      shift(c.io, 0, 0)
      step()

      shift(c.io, 0, 1)
      step()

      shift(c.io, 1, 1)
      step()

      shift(c.io, 1, 0)
      step()

      shift(c.io, 0, 0)
      step()

      poke(c.io.chainIn.shift, 1)
      check(c.io.chainOut.shift, 1)
      check(c.io.chainOut.capture, 0)
      check(c.io.chainOut.update, 0)

      poke(c.io.chainIn.data, 0)
      step()
      check(c.io.chainOut.data, 0)

      poke(c.io.chainIn.data, 1)
      step()
      check(c.io.chainOut.data, 1)

      poke(c.io.chainIn.data, 1)
      step()
      check(c.io.chainOut.data, 1)

      poke(c.io.chainIn.data, 0)
      step()
      check(c.io.chainOut.data, 0)

      poke(c.io.chainIn.data, 1)
      step()
      check(c.io.chainOut.data, 1)
    }
  }

  "8-bit capture chain" should "work" in {
    test(CaptureChain(UInt(8.W))) { implicit t => c =>
      // Test capture and shift
      poke(c.io.capture.bits, "01001110".b)
      capture(c.io)
      step()

      shift(c.io, 0, 1)
      step()

      shift(c.io, 1, 1)
      step()

      shift(c.io, 1, 0)
      step()

      shift(c.io, 1, 1)
      step()

      shift(c.io, 0, 1)
      step()
    }
  }

  "8-bit capture-update chain" should "work" in {
    test(CaptureUpdateChain(UInt(8.W))) { implicit t => c =>
      // Test capture and shift
      poke(c.io.capture.bits, "01001110".b)
      capture(c.io)
      step()

      poke(c.io.chainIn.capture, 0)
      poke(c.io.chainIn.shift, 1)
      check(c.io.chainOut.shift, 1)
      check(c.io.chainOut.capture, 0)
      check(c.io.chainOut.update, 0)
      check(c.io.capture.capture, 0)
      check(c.io.update.valid, 0)

      check(c.io.update.bits, "01001110".b)  // whitebox testing
      shift(c.io, 0, 1)
      step()

      check(c.io.update.bits, "10100111".b)  // whitebox testing
      shift(c.io, 1, 1)
      step()

      nop(c.io)  // for giggles
      step()

      check(c.io.update.bits, "11010011".b)  // whitebox testing
      shift(c.io, 1, 0)
      step()

      check(c.io.update.bits, "01101001".b)  // whitebox testing
      shift(c.io, 1, 1)
      step()

      // Test update
      update(c.io)
      check(c.io.update.bits, "10110100".b)
      step()

      // Capture-idle-update tests
      poke(c.io.capture.bits, "00000000".b)
      capture(c.io)
      step()

      nop(c.io)
      step()
      nop(c.io)
      step()
      nop(c.io)
      step()
      nop(c.io)
      step()

      update(c.io)
      check(c.io.update.bits, "00000000".b)
      step()

      // Capture-update tests
      poke(c.io.capture.bits, "11111111".b)
      capture(c.io)
      step()

      update(c.io)
      check(c.io.update.bits, "11111111".b)
      step()
    }
  }

  "Vector and Bundle capture-update chains" should "be ordered properly" in {
    class InnerBundle extends Bundle {
      val d = Bool()
      val c = UInt(2.W)

      override def cloneType: this.type = (new InnerBundle).asInstanceOf[this.type]
    }

    class TestBundle extends Bundle {
      val b = UInt(2.W)
      val a = Bool()  // this is second to ensure Bundle order is preserved
      val c = Vec(3, Bool())
      val x = new InnerBundle()

      override def cloneType: this.type = (new TestBundle).asInstanceOf[this.type]

      // Ordering should be:
      // b[1:0] a c[2] c[1] c[0] x.d x.c[1:0]
    }
    test(CaptureUpdateChain(new TestBundle)) { implicit t => c =>
      val cap = c.io.capture.bits
      val upd = c.io.update.bits

      // Test capture, shift, and update
      poke(cap.b, "01".b)
      poke(cap.a, 1)
      poke(cap.c(2), 1)
      poke(cap.c(1), 0)
      poke(cap.c(0), 0)
      poke(cap.x.d, 1)
      poke(cap.x.c, "01".b)

      capture(c.io)
      step()

      shift(c.io, 1, 1)  // capture LSB, update LSB
      step()
      shift(c.io, 0, 1)
      step()
      shift(c.io, 1, 0)
      step()
      shift(c.io, 0, 0)
      step()
      shift(c.io, 0, 0)
      step()
      shift(c.io, 1, 1)
      step()
      shift(c.io, 1, 0)
      step()
      shift(c.io, 1, 0)
      step()
      shift(c.io, 0, 1)  // capture MSB, update MSB
      step()

      update(c.io)
      check(upd.b, "10".b)
      check(upd.a, 0)
      check(upd.c(2), 1)
      check(upd.c(1), 0)
      check(upd.c(0), 0)
      check(upd.x.d, 0)
      check(upd.x.c, "11".b)
      step()
    }
  }

  "Capture-update chain with larger capture width" should "work" in {
    test(CaptureUpdateChain(UInt(4.W), UInt(2.W))) { implicit t => c =>
      poke(c.io.capture.bits, "1101".b)
      capture(c.io)
      step()

      shift(c.io, 1, 0)  // capture LSB, update LSB
      step()
      shift(c.io, 0, 1)  // update MSB
      step()
      shift(c.io, 1, 1)
      step()
      shift(c.io, 1, 1)  // capture MSB
      step()

      update(c.io)
      check(c.io.update.bits, "10".b)
    }
  }


  "Capture-update chain with larger update width" should "work" in {
    test(CaptureUpdateChain(UInt(2.W), UInt(4.W))) { implicit t => c =>
      poke(c.io.capture.bits, "10".b)
      capture(c.io)
      step()

      shift(c.io, 0, 1)  // capture LSB, update LSB
      step()
      shift(c.io, 1, 0)  // capture MSB
      step()
      shift(c.io, 0, 1)
      step()
      shift(c.io, 0, 1)  // update MSB
      step()

      update(c.io)
      check(c.io.update.bits, "1101".b)
    }
  }
}
