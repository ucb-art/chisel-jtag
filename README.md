# chisel-jtag
JTAG TAP generator for (and written in) Chisel

## Introduction
JTAG is a communications / transport protocol used for debugging chips by providing a scan chain of connected devices and an instruction register and data register(s) per device. A common use of JTAG is for boundary scan (to verify component connectivity on a circuit board), but JTAG is also used as a communication protocol for debugging (i.e. flashing and stepping processors).

### This Design
This design implements the JTAG TAP (Test Access Port) controller, including the JTAG state machine, instruction registers, and ports to connect user data registers. A TAP generator is also included, which provides instruction register decode and maps user scan chains to instructions.

The implementation is clocked from the JTAG clock, allowing the JTAG block to function independently of whether the system clock is running (or even works). This is NOT an optimized design, focusing instead on readability and an easy-to-use API.

### Experimental!
This is still an experimental design and the API is still subject to change.

## Using
### The Basics
Import the necessary packages:

```
import jtag._
```

Define your test block module, which encapsulates the JTAG TAP controller, user data scan chains, and any additional logic needed. This block must be clocked from JTAG TCK and should expose the JTAG IO pins (TCK, TMS, TDI, TDO).

```
class JtagBlock (modClock: Clock) extends Module(override_clock=Some(modClock)) {
  val io = IO(new JtagIO)  // JtagIO is a Bundle of {TCK, TMS, TDI, and TDO}, where TDO is a Tristate Bundle of {data and driven}
}
```

Invoke the TAP generator to instantiate the TAP controller logic and some predefined blocks (like the required BYPASS and optional IDCODE registers):

```
val tap = JtagTapGenerator(irLength, Map(), idcode=Some((0, JtagIdcode(0xA, 0x123, 0x42))))
io.jtag <> tap.io.jtag
io.output <> tap.io.output
io.status <> tap.io.status
```

`JtagTapGenerator` takes these arguments:
- `irLength`: Int - length, in bits, of the instruction register.
- `instructions`: Map[Chain, BigInt] - a Map of data registers (Chains) to the instruction code that selects them.
- `idcode`: Option[(BigInt, BigInt)] - optional IDCODE generator. A value of None means to not generate a IDCODE register (and BYPASS, with an instruction code of all ones, will be selected as the initial instruction), while a value of (instruction code, idcode) generates the 32-bit IDCODE register and selects its corresponding instruction code as the initial instruction.

### User Data Chains


## Hardware Verification
This generator has been used in these designs:

### iCE40 Breakout
Planned, to be done,

## TODOs
Some features are yet to be implemented:
- Ability to annotate arbitrary registers in arbitrary modules to be written to / read from using JTAG. This will probably be implemented as a FIRRTL transform.
- Capture-only and update-only scan chains. This is mainly an optimization.
- Boundary-scan, technically needed to chain JTAG compliance. This may be difficult as it requires clock crossing domains and messing up can result in logic glitches that fry a chip.
- Other JTAG optional instructions, like TMP.
- Asynchronous reset through TRST. This is an optional part of the spec and is of low importance.

Some cleaning up is also to be done:
- Multiclock using chisel3's proposed `withClock` API, when it's done.
