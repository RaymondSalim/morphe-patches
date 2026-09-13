# Ghidra headless script (PyGhidra): decompile functions and print pseudo-C.
# Args: one or more function entry addresses (hex). Addresses are Ghidra
# addresses (Castle Clashers project: dump.cs VA + 0x100000).
import traceback

try:
    from ghidra.app.decompiler import DecompInterface, DecompileOptions
    from ghidra.program.flatapi import FlatProgramAPI
    from ghidra.util.task import ConsoleTaskMonitor

    args = getScriptArgs()
    af = currentProgram.getAddressFactory().getDefaultAddressSpace()
    opts = DecompileOptions()
    di = DecompInterface()
    di.setOptions(opts)
    di.openProgram(currentProgram)
    monitor = ConsoleTaskMonitor()
    for a in args:
        addr = af.getAddress(int(a, 16))
        fn = currentProgram.getFunctionManager().getFunctionAt(addr)
        if fn is None:
            fn = FlatProgramAPI(currentProgram).createFunction(addr, 'sub_' + a[2:])
        if fn is None:
            print('no function at %s and createFunction failed' % a, flush=True)
            continue
        print('=== decompile %s ===' % a, flush=True)
        res = di.decompileFunction(fn, 300, monitor)
        if res.decompileCompleted():
            print(res.getDecompiledFunction().getC(), flush=True)
        else:
            print('decompile FAILED: %s' % res.getErrorMessage(), flush=True)
except Exception:
    traceback.print_exc()
