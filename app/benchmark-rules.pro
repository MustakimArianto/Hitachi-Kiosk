# Extra R8 rules for the benchmarkRelease build only (DEV-031). It adds the :core:testing fakes, whose
# test-only dependencies (JUnit, coroutines-test) are never called at runtime.
