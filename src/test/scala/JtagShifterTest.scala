// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

class JtagBypassChainTest(val c: JtagBypassChain) extends PeekPokeTester(c) {
  poke(c.io.chainIn.shift, 0)
  poke(c.io.chainIn.data, 0)
  poke(c.io.chainIn.capture, 0)
  poke(c.io.chainIn.update, 0)

  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 0)
  expect(c.io.chainOut.update, 0)

  // Test shift functionality
  poke(c.io.chainIn.shift, 1)
  expect(c.io.chainOut.shift, 1)
  expect(c.io.chainOut.capture, 0)
  expect(c.io.chainOut.update, 0)

  poke(c.io.chainIn.data, 0)
  step(1)
  expect(c.io.chainOut.data, 0)

  poke(c.io.chainIn.data, 1)
  step(1)
  expect(c.io.chainOut.data, 1)

  poke(c.io.chainIn.data, 1)
  step(1)
  expect(c.io.chainOut.data, 1)

  poke(c.io.chainIn.data, 0)
  step(1)
  expect(c.io.chainOut.data, 0)

  poke(c.io.chainIn.data, 1)
  step(1)
  expect(c.io.chainOut.data, 1)

  // Test capture functionality
  poke(c.io.chainIn.shift, 0)
  poke(c.io.chainIn.capture, 1)
  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 1)
  expect(c.io.chainOut.update, 0)
  step(1)
  expect(c.io.chainOut.data, 0)
}

class JtagCaptureUpdateChainTest(val c: JtagCaptureUpdateChain) extends PeekPokeTester(c) {
  import BinaryParse._

  poke(c.io.chainIn.shift, 0)
  poke(c.io.chainIn.data, 0)
  poke(c.io.chainIn.capture, 0)
  poke(c.io.chainIn.update, 0)

  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 0)
  expect(c.io.chainOut.update, 0)

  // Test capture and shift
  poke(c.io.capture.bits, "01001110".b)
  poke(c.io.chainIn.capture, 1)
  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 1)
  expect(c.io.chainOut.update, 0)
  expect(c.io.capture.capture, 1)
  expect(c.io.update.valid, 0)

  step(1)

  poke(c.io.chainIn.capture, 0)
  poke(c.io.chainIn.shift, 1)
  expect(c.io.chainOut.shift, 1)
  expect(c.io.chainOut.capture, 0)
  expect(c.io.chainOut.update, 0)
  expect(c.io.capture.capture, 0)
  expect(c.io.update.valid, 0)

  expect(c.io.chainOut.data, 0)
  expect(c.io.update.bits, "01001110".b)  // whitebox testing
  poke(c.io.chainIn.data, 1)

  step(1)

  expect(c.io.chainOut.data, 1)
  expect(c.io.update.bits, "10100111".b)  // whitebox testing
  poke(c.io.chainIn.data, 1)

  step(1)

  expect(c.io.chainOut.data, 1)
  expect(c.io.update.bits, "11010011".b)  // whitebox testing
  poke(c.io.chainIn.data, 0)

  step(1)

  expect(c.io.chainOut.data, 1)
  expect(c.io.update.bits, "01101001".b)  // whitebox testing
  poke(c.io.chainIn.data, 1)

  step(1)

  expect(c.io.chainOut.data, 0)
  expect(c.io.update.bits, "10110100".b)  // whitebox testing
  poke(c.io.chainIn.data, 1)

  // Test update
  poke(c.io.chainIn.shift, 0)
  poke(c.io.chainIn.update, 1)
  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 0)
  expect(c.io.chainOut.update, 1)
  expect(c.io.capture.capture, 0)
  expect(c.io.update.bits, "10110100".b)
  expect(c.io.update.valid, 1)

  step(1)

  // Capture-idle-update tests
  poke(c.io.chainIn.update, 0)
  poke(c.io.capture.bits, "00000000".b)
  poke(c.io.chainIn.capture, 1)
  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 1)
  expect(c.io.chainOut.update, 0)
  expect(c.io.capture.capture, 1)
  expect(c.io.update.valid, 0)

  step(1)

  poke(c.io.chainIn.capture, 0)

  step(4)

  poke(c.io.chainIn.update, 1)
  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 0)
  expect(c.io.chainOut.update, 1)
  expect(c.io.capture.capture, 0)
  expect(c.io.update.bits, "00000000".b)
  expect(c.io.update.valid, 1)

  // Capture-update tests
  poke(c.io.chainIn.update, 0)
  poke(c.io.capture.bits, "11111111".b)
  poke(c.io.chainIn.capture, 1)
  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 1)
  expect(c.io.chainOut.update, 0)
  expect(c.io.capture.capture, 1)
  expect(c.io.update.valid, 0)

  step(1)

  poke(c.io.chainIn.capture, 0)
  poke(c.io.chainIn.update, 1)
  expect(c.io.chainOut.shift, 0)
  expect(c.io.chainOut.capture, 0)
  expect(c.io.chainOut.update, 1)
  expect(c.io.capture.capture, 0)
  expect(c.io.update.bits, "11111111".b)
  expect(c.io.update.valid, 1)
}

class JtagShifterSpec extends ChiselFlatSpec {
  "JTAG bypass chain" should "work" in {
    Driver(() => new JtagBypassChain()) {
      c => new JtagBypassChainTest(c)
    } should be (true)
  }
  "8-bit JTAG capture-update chain" should "work" in {
    Driver(() => new JtagCaptureUpdateChain(8)) {
      c => new JtagCaptureUpdateChainTest(c)
    } should be (true)
  }
}