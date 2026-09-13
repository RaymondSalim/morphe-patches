# Ghidra headless script. Disassembles one or more [start, start+len) ranges
# and prints one instruction per line. Args: repeated <startAddrHex> <lenHex>.
# Addresses are Ghidra addresses (Castle Clashers project: dump.cs VA + 0x100000).
#
# Ghidra <= 11 (Jython):
#   analyzeHeadless apk/re/ghidra-project castle -process libil2cpp.so -noanalysis \
#     -scriptPath apk/re/scripts -postScript disasm_multi.py <startAddrHex> <lenHex> \
#     [<startAddrHex> <lenHex> ...]
#
# Ghidra >= 12 (Jython removed; run via PyGhidra against the existing project):
#   env -u JAVA_HOME PATH="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:$PATH" \
#   apk/re/pyghidra-venv/bin/python -c "import pyghidra; \
#     pyghidra.start(install_dir='/opt/homebrew/opt/ghidra/libexec'); \
#     pyghidra.run_script('$PWD/apk/re/inputs/libil2cpp.so', 'tools/ghidra/disasm_multi.py', \
#       project_location='$PWD/apk/re/ghidra-project', project_name='castle', \
#       script_args=['<startAddrHex>', '<lenHex>', ...], nested_project_location=False, analyze=False)"
from ghidra.program.model.address import AddressSet
from ghidra.app.cmd.disassemble import DisassembleCommand

args = getScriptArgs()
af = currentProgram.getAddressFactory().getDefaultAddressSpace()
for i in range(0, len(args), 2):
    start = af.getAddress(int(args[i], 16))
    length = int(args[i + 1], 16)
    end = start.add(length)
    DisassembleCommand(start, AddressSet(start, end), True).applyTo(currentProgram, monitor)
    print('=== range %s len %#x ===' % (args[i], length))
    for ins in currentProgram.getListing().getInstructions(start, True):
        print('%s  %s' % (ins.getAddress(), ins))
        if ins.getAddress() >= end:
            break
