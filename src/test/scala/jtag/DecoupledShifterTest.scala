// See LICENSE for license details.

package jtag

import org.scalatest._
import chisel3._

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
      shift(c.io, 0, 1)  // capture MSB, update MSB - (empty), bits
      check(c.io.interface.valid, false)
      step()

      update(c.io)
      check(c.io.interface.bits, "1101".b)
      check(c.io.interface.valid, true)

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
      shift(c.io, 0, 1)  // capture MSB, update MSB - (empty), bits
      check(c.io.interface.valid, false)
      step()

      update(c.io)
      check(c.io.interface.valid, false)
    }
  }

  //TODO: This test should work, what has broken?
  "Decoupled sink chain" should "work" ignore {
    test(new DecoupledSinkChain(UInt(4.W))) { implicit t => c =>
      nop(c.io)
      step()

      // test capture-update with interface valid
      poke(c.io.interface.valid, true)
      poke(c.io.interface.bits, "1101".b)
      capture(c.io)
      check(c.io.interface.ready, true)
      step()

      shift(c.io, 1, 0)  // capture LSB, update LSB - bits, (empty)
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
      check(c.io.interface.ready, true)
      step()

      shift(c.io, 1, 0)  // capture LSB, update LSB - bits, (empty)
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
