/** \file etl_profile.h
 *
 * Project-wide configuration for the vendored ETL (Embedded Template Library).
 *
 * ETL resolves its configuration by looking for this header *before* it includes any
 * of its own headers (see `etl/platform.h`, which does `__has_include("etl_profile.h")`).
 * Placing it here — in the `etl` component's public include directory — guarantees every
 * component that `REQUIRES etl` picks it up through the normal include path.
 *
 * The single most important setting below is what we *don't* define: `ETL_THROW_EXCEPTIONS`.
 * Leaving it undefined makes ETL compile as non-throwing, which is required because this
 * project is built with `-fno-exceptions`. On an error, ETL then either invokes the
 * user-supplied `etl::error_handler` callback (only when `ETL_LOG_ERRORS` is defined) or
 * returns a safe default silently.
 */
#ifndef ETL_PROFILE_H_INCLUDED
#define ETL_PROFILE_H_INCLUDED

//*****************************************************************************
// Target & toolchain capabilities (generic C++20, no C++ standard-library use).
//*****************************************************************************
#define ETL_TARGET_DEVICE_GENERIC
#define ETL_TARGET_OS_NONE
#define ETL_COMPILER_GENERIC

#define ETL_CPP11_SUPPORTED 1
#define ETL_CPP14_SUPPORTED 1
#define ETL_CPP17_SUPPORTED 1
#define ETL_CPP20_SUPPORTED 1
#define ETL_CPP23_SUPPORTED 0

#define ETL_NO_NULLPTR_SUPPORT 0
#define ETL_NO_LARGE_CHAR_SUPPORT 0

// ETL stays fully self-contained: it must not pull in the C++ standard library's
// containers/algorithms/type-traits. This keeps the flash/ROM footprint down and
// avoids any coupling to libstdc++ internals beyond the freestanding headers ETL itself
// needs (`<stdint.h>`, `<stddef.h>`, `<limits.h>`).
#define ETL_NO_STL

//*****************************************************************************
// Error handling: non-throwing (see the file header note). Bounds checks stay
// enabled — safety over the last few bytes of code size.
//*****************************************************************************
#define ETL_NO_CHECKS 0

#endif // ETL_PROFILE_H_INCLUDED
