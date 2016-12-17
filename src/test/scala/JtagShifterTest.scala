// See LICENSE for license details.

package jtag.test

import org.scalatest._

import chisel3.iotesters.experimental.ImplicitPokeTester

import chisel3._
import jtag._

trait ChainIOUtils extends ImplicitPokeTester {
  // CaptureUpdateChain test utilities
  def nop(io: ChainIO)(implicit t: InnerTester) {
    io.chainIn.shift <<= 0
    io.chainIn.capture <<= 0
    io.chainIn.update <<= 0

    io.chainOut.shift ?== 0
    io.chainOut.capture ?== 0
    io.chainOut.update ?== 0
  }

  def capture(io: ChainIO)(implicit t: InnerTester) {
    io.chainIn.shift <<= 0
    io.chainIn.capture <<= 1
    io.chainIn.update <<= 0

    io.chainOut.shift ?== 0
    io.chainOut.capture ?== 1
    io.chainOut.update ?== 0
  }

  def shift(io: ChainIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    io.chainIn.shift <<= 1
    io.chainIn.capture <<= 0
    io.chainIn.update <<= 0

    io.chainOut.shift ?== 1
    io.chainOut.capture ?== 0
    io.chainOut.update ?== 0

    io.chainOut.data ?== expectedDataOut
    io.chainIn.data <<= dataIn
  }

  def update(io: ChainIO)(implicit t: InnerTester) {
    io.chainIn.shift <<= 0
    io.chainIn.capture <<= 0
    io.chainIn.update <<= 1

    io.chainOut.shift ?== 0
    io.chainOut.capture ?== 0
    io.chainOut.update ?== 1

  }

  // CaptureChain test utilities
  def nop(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    nop(io.asInstanceOf[ChainIO])
    io.capture.capture ?== 0
  }

  def capture(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    capture(io.asInstanceOf[ChainIO])
    io.capture.capture ?== 1
  }

  def shift(io: CaptureChain[Data]#ModIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    shift(io.asInstanceOf[ChainIO], expectedDataOut, dataIn)
    io.capture.capture ?== 0
  }

  def update(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    update(io.asInstanceOf[ChainIO])
    io.capture.capture ?== 0
  }

  // CaptureUpdateChain test utilities
  def nop(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    nop(io.asInstanceOf[ChainIO])
    io.capture.capture ?== 0
    io.update.valid ?== 0
  }

  def capture(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    capture(io.asInstanceOf[ChainIO])
    io.capture.capture ?== 1
    io.update.valid ?== 0
  }

  def shift(io: CaptureUpdateChain[Data, Data]#ModIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    shift(io.asInstanceOf[ChainIO], expectedDataOut, dataIn)
    io.capture.capture ?== 0
    io.update.valid ?== 0
  }

  def update(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    update(io.asInstanceOf[ChainIO])
    io.capture.capture ?== 0
    io.update.valid ?== 1
  }
}

class JtagShifterSpec extends FlatSpec with ChainIOUtils {
  import BinaryParse._

  "JTAG bypass chain" should "work" in {
    run(JtagBypassChain()) { implicit t => c =>
      nop(c.io)
      step()

      // Shift without expect
      c.io.chainIn.shift <<= 1
      c.io.chainIn.data <<= 0
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

      c.io.chainIn.shift <<= 1
      c.io.chainOut.shift ?== 1
      c.io.chainOut.capture ?== 0
      c.io.chainOut.update ?== 0

      c.io.chainIn.data <<= 0
      step()
      c.io.chainOut.data ?== 0

      c.io.chainIn.data <<= 1
      step()
      c.io.chainOut.data ?== 1

      c.io.chainIn.data <<= 1
      step()
      c.io.chainOut.data ?== 1

      c.io.chainIn.data <<= 0
      step()
      c.io.chainOut.data ?== 0

      c.io.chainIn.data <<= 1
      step()
      c.io.chainOut.data ?== 1
    }
  }

  "8-bit capture chain" should "work" in {
    run(CaptureChain(UInt(8.W))) { implicit t => c =>
      // Test capture and shift
      c.io.capture.bits <<= "01001110".b
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
    run(CaptureUpdateChain(UInt(8.W))) { implicit t => c =>
      // Test capture and shift
      c.io.capture.bits <<= "01001110".b
      capture(c.io)
      step()

      c.io.chainIn.capture <<= 0
      c.io.chainIn.shift <<= 1
      c.io.chainOut.shift ?== 1
      c.io.chainOut.capture ?== 0
      c.io.chainOut.update ?== 0
      c.io.capture.capture ?== 0
      c.io.update.valid ?== 0

      c.io.update.bits ?== "01001110".b  // whitebox testing
      shift(c.io, 0, 1)
      step()

      c.io.update.bits ?== "10100111".b  // whitebox testing
      shift(c.io, 1, 1)
      step()

      nop(c.io)  // for giggles
      step()

      c.io.update.bits ?== "11010011".b  // whitebox testing
      shift(c.io, 1, 0)
      step()

      c.io.update.bits ?== "01101001".b  // whitebox testing
      shift(c.io, 1, 1)
      step()

      // Test update
      update(c.io)
      c.io.update.bits ?== "10110100".b
      step()

      // Capture-idle-update tests
      c.io.capture.bits <<= "00000000".b
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
      c.io.update.bits ?== "00000000".b
      step()

      // Capture-update tests
      c.io.capture.bits <<= "11111111".b
      capture(c.io)
      step()

      update(c.io)
      c.io.update.bits ?== "11111111".b
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
    run(CaptureUpdateChain(new TestBundle)) { implicit t => c =>
      val cap = c.io.capture.bits
      val upd = c.io.update.bits

      // Test capture, shift, and update
      cap.b <<= "01".b
      cap.a <<= 1
      cap.c(2) <<= 1
      cap.c(1) <<= 0
      cap.c(0) <<= 0
      cap.x.d <<= 1
      cap.x.c <<= "01".b

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
      upd.b ?== "10".b
      upd.a ?== 0
      upd.c(2) ?== 1
      upd.c(1) ?== 0
      upd.c(0) ?== 0
      upd.x.d ?== 0
      upd.x.c ?== "11".b
      step()
    }
  }

  "Capture-update chain with larger capture width" should "work" in {
    run(CaptureUpdateChain(UInt(4.W), UInt(2.W))) { implicit t => c =>
      c.io.capture.bits <<= "1101".b
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
      c.io.update.bits ?== "10".b
    }
  }


  "Capture-update chain with larger update width" should "work" in {
    run(CaptureUpdateChain(UInt(2.W), UInt(4.W))) { implicit t => c =>
      c.io.capture.bits <<= "10".b
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
      c.io.update.bits ?== "1101".b
    }
  }
}
