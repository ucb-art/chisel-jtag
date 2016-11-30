// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

trait JtagTestUtilities extends PeekPokeTester[chisel3.Module] {
  def jtagLow(io: JtagIO) {
    poke(io.TCK, 0)
    step(1)
  }
  def jtagHigh(io: JtagIO) {
    poke(io.TCK, 1)
    step(1)
  }

  /**
    *
    */
  def tmsReset(io: JtagIO) {
    poke(io.TMS, 1)
    jtagHigh(io)  // set to known state
    for (_ <- 0 until 5) {
      jtagLow(io)
      jtagHigh(io)
    }
  }
}

class JtagTapTester(val c: JtagTap) extends PeekPokeTester(c) {
  val jtag = c.io.jtag

  poke(jtag.TCK, 0)
  step(1)

  poke(jtag.TDI, 1)
  poke(jtag.TCK, 1)  // TDI should latch here
  step(1)
  poke(jtag.TDI, 0)  // ensure that TDI isn't latched after TCK goes low
  poke(jtag.TCK, 0)
  step(1)
  expect(jtag.TDO, 1)

  poke(jtag.TCK, 1)  // previous TDI transition should latch here
  step(1)
  expect(jtag.TDO, 1)  // ensure TDO doesn't change on rising edge
  poke(jtag.TDI, 1)
  poke(jtag.TCK, 0)
  step(1)
  expect(jtag.TDO, 0)  // previous TDI transition seen on output here

  poke(jtag.TCK, 1)
  step(1)
  expect(jtag.TDO, 0)
  poke(jtag.TDI, 0)
  poke(jtag.TCK, 0)
  step(1)
  expect(jtag.TDO, 1)
}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP" should "work" in {
    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => new JtagTap(2), backendType="verilator") {
      c => new JtagTapTester(c)
    } should be (true)
  }
}
