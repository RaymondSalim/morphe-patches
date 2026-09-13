# Ghidra headless script (PyGhidra): force-rebuild disassembly for [start, start+len)
# ranges. Args: repeated <startAddrHex> <lenHex>. Addresses are Ghidra addresses
# (Castle Clashers project: dump.cs VA + 0x100000).
#
# Clears existing code units, disassembles with DisassembleCommand, then walks the
# range printing one instruction per line; addresses with no instruction print as
# <undefined> so gaps stay visible. Failures print a traceback because the
# GhidraScript harness swallows script exceptions silently.
#
# Ghidra 12 notes: FlatProgramAPI lives in ghidra.program.flatapi and its
# disassemble call resolves to the boolean AddressSetView overload under jpype, so
# DisassembleCommand is used instead; python comparison operators on Address
# objects raise TypeError, so comparisons go through Address.compareTo.
from ghidra.program.model.address import AddressSet
from ghidra.app.cmd.disassemble import DisassembleCommand
import traceback

try:
    args = getScriptArgs()
    space = currentProgram.getAddressFactory().getDefaultAddressSpace()
    listing = currentProgram.getListing()
    for i in range(0, len(args), 2):
        start = space.getAddress(int(args[i], 16))
        length = int(args[i + 1], 16)
        end = start.add(length)
        listing.clearCodeUnits(start, end, True)
        DisassembleCommand(start, AddressSet(start, end), True).applyTo(currentProgram, monitor)
        print('=== range %s len %#x ===' % (args[i], length), flush=True)
        a = start
        while a.compareTo(end) < 0:
            ins = listing.getInstructionAt(a)
            if ins is None:
                print('%s  <undefined>' % a, flush=True)
            else:
                print('%s  %s' % (ins.getAddress(), ins), flush=True)
                a = ins.getMaxAddress()
            a = a.add(1)
except Exception:
    traceback.print_exc()
