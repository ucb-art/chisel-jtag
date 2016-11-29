// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import jtag._


class JtagTapTester(val c: JtagTap) extends PeekPokeTester(c) {
  poke(c.io.TCK, 1)
  step(1)

  poke(c.io.TDI, 1)
  poke(c.io.TCK, 0)  // TDI should latch here
  step(1)
  poke(c.io.TDI, 0)  // ensure that TDI isn't latched after TCK goes high
  poke(c.io.TCK, 1)
  step(1)
  expect(c.io.TDO, 1)

  poke(c.io.TCK, 0)  // previous TDI transition should latch here
  step(1)
  expect(c.io.TDO, 1)  // ensure TDO doesn't change on falling edge
  poke(c.io.TDI, 1)
  poke(c.io.TCK, 1)
  step(1)
  expect(c.io.TDO, 0)  // previous TDI transition seen on output here

  poke(c.io.TCK, 0)
  step(1)
  expect(c.io.TDO, 0)
  poke(c.io.TDI, 0)
  poke(c.io.TCK, 1)
  step(1)
  expect(c.io.TDO, 1)
}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP" should "work" in {
    //Driver(() => new JtagTap()) {  // multiclock doesn't work here yet
    Driver(() => new JtagTap(), backendType="verilator") {
      c => new JtagTapTester(c)
    } should be (true)
  }
}
