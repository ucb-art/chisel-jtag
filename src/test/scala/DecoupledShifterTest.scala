// See LICENSE for license details.

package jtag.test

import org.scalatest._

import chisel3.iotesters.experimental.ImplicitPokeTester

import chisel3._
import jtag._

class DecoupledShifterSpec extends FlatSpec with ChainIOUtils {
  import BinaryParse._

  "Decoupled source chain" should "work" in {
    test(new DecoupledSourceChain(UInt(4.W))) { implicit t => c =>
      nop(c.io)
      step()

      // test capture-update with interface ready
      poke(c.io.interface.ready, true)
      capture(c.io)
      check(c.io.interface.valid, false)
      step()

      shift(c.io, 1, 1)  // capture LSB, update LSB - ready, bits
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 0)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)  // capture MSB, update MSB - (empty), valid
      check(c.io.interface.valid, false)
      step()

      update(c.io)
      check(c.io.interface.valid, true)
      check(c.io.interface.bits, "1101".b)

      // test capture-update with interface ready but valid request false
      poke(c.io.interface.ready, true)
      capture(c.io)
      check(c.io.interface.valid, false)
      step()

      shift(c.io, 1, 1)  // capture LSB, update LSB - ready, bits
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 0)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 0)  // capture MSB, update MSB - (empty), valid
      check(c.io.interface.valid, false)
      step()

      update(c.io)
      check(c.io.interface.valid, false)

      // test capture-update with interface not ready
      poke(c.io.interface.ready, false)
      capture(c.io)
      step()

      poke(c.io.interface.ready, true)
      shift(c.io, 0, 1)  // capture LSB, update LSB - ready, bits
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 0)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)
      check(c.io.interface.valid, false)
      step()
      shift(c.io, 0, 1)  // capture MSB, update MSB - (empty), valid
      check(c.io.interface.valid, false)
      step()

      update(c.io)
      check(c.io.interface.valid, false)
    }
  }

  "Decoupled sink chain" should "work" in {
    test(new DecoupledSinkChain(UInt(4.W))) { implicit t => c =>
      nop(c.io)
      step()

      // test capture-update with interface valid
      poke(c.io.interface.valid, true)
      poke(c.io.interface.bits, "1101".b)
      capture(c.io)
      check(c.io.interface.ready, false)
      step()

      shift(c.io, 1, 1)  // capture LSB, update LSB - bits, ready
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 0, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)  // capture MSB, update MSB - valid, (empty)
      check(c.io.interface.ready, false)
      step()

      update(c.io)
      check(c.io.interface.ready, true)

      // test capture-update with interface valid but request invalid (peeking)
      poke(c.io.interface.valid, true)
      poke(c.io.interface.bits, "1101".b)
      capture(c.io)
      check(c.io.interface.ready, false)
      step()

      shift(c.io, 1, 0)  // capture LSB, update LSB - bits, ready
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 0, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)  // capture MSB, update MSB - valid, (empty)
      check(c.io.interface.ready, false)
      step()

      update(c.io)
      check(c.io.interface.ready, false)
      
      // test capture-update with interface not ready
      poke(c.io.interface.valid, false)
      capture(c.io)
      check(c.io.interface.ready, false)
      step()

      shift(c.io, 1, 1)  // capture LSB, update LSB - bits, ready
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 0, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 1, 0)
      check(c.io.interface.ready, false)
      step()
      shift(c.io, 0, 0)  // capture MSB, update MSB - valid, (empty)
      check(c.io.interface.ready, false)
      step()

      update(c.io)
      check(c.io.interface.ready, false)
    }
  }  
}
