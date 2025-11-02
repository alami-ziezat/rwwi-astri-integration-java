# Magik Interop Utilities Investigation

**Date:** 2025-10-30
**Purpose:** Investigate available methods in MagikVectorUtils and MagikInteropUtils for property_list access

## Question
Can we use `MagikVectorUtils.getObjectMap()` to directly convert property_list to Java Map?

## Answer
**No** - There is no `getObjectMap()` method available in the Magik interop libraries.

## Investigation Results

### 1. MagikVectorUtils - Available Methods

**Location:** `com.gesmallworld.magik.interop.MagikVectorUtils`
**JAR:** `C:\Smallworld\core\libs\com.gesmallworld.magik.interop-5.3.6.0-412.jar`

**Available Public Methods:**

#### Array Extraction (Magik → Java):
- `byte[] getByteArray(Object)` - Extract byte array
- `short[] getShortArray(Object)` - Extract short array
- `int[] getIntArray(Object)` - Extract int array
- `long[] getLongArray(Object)` - Extract long array
- `float[] getFloatArray(Object)` - Extract float array
- `double[] getDoubleArray(Object)` - Extract double array
- **`Object[] getObjectArray(Object)`** - **Extract Object array** ✅ (What we use)

#### Vector Creation (Java → Magik):
- `Object createMagikVector(byte[])` - Create Magik vector from byte array
- `Object createMagikVector(short[])` - Create Magik vector from short array
- `Object createMagikVector(int[])` - Create Magik vector from int array
- `Object createMagikVector(long[])` - Create Magik vector from long array
- `Object createMagikVector(float[])` - Create Magik vector from float array
- `Object createMagikVector(double[])` - Create Magik vector from double array
- `Object createMagikVector(Object[])` - Create Magik vector from Object array
- `Object createMagikVector(String...)` - Create Magik vector from strings
- Various overloads with additional Object parameter

**Key Finding:** Only array-based methods available. No Map conversion methods.

### 2. MagikInteropUtils - Available Methods

**Location:** `com.gesmallworld.magik.interop.MagikInteropUtils`
**JAR:** `C:\Smallworld\core\libs\com.gesmallworld.magik.interop-5.3.6.0-412.jar`

**Available Public Methods:**

#### Type Conversion (Magik → Java):
- `boolean fromMagikBoolean(Object)`
- `short fromMagikShort(Object)`
- `int fromMagikInteger(Object)`
- `long fromMagikLong(Object)`
- `BigInteger fromMagikBignum(Object)`
- `float fromMagikFloat(Object)`
- `double fromMagikDouble(Object)`
- **`String fromMagikString(Object)`** ✅ (Used for string extraction)

#### Type Conversion (Java → Magik):
- `Object toMagikBoolean(boolean)`
- `Object toMagikInteger(int)`
- `Object toMagikInteger(long)`
- `Object toMagikBignum(BigInteger)`
- `Object toMagikDouble(double)`
- **`Object toMagikString(String)`** ✅ (Used for return values)
- **`Object toMagikSymbol(String)`** ✅ (Used for key conversion)

#### Utility Methods:
- `Object toMultipleResults(Object...)`
- `Object raiseMagikCondition(String, Object...)`
- `IllegalArgumentException newBadArgsError(String, Object)`
- `String getEnvironmentVariable(String)`
- `void setEnvironmentVariable(String, String)`

**Key Finding:** Type conversion methods only. No Map or property_list-specific methods.

### 3. Other Interop Classes

Checked for other utility classes:
- `com.gesmallworld.magik.interop.JavaToMagikActivator` - Activation only
- No other utility classes with Map conversion

## Conclusion

### Best Approach for property_list Access

Since there is **no `getObjectMap()` method**, the **array-based approach is the correct solution**.

#### Why Array-Based Works:

Magik `property_list.new_with(:key1, val1, :key2, val2)` internally stores data as:
```
[:key1, val1, :key2, val2, ...]
```

Using `MagikVectorUtils.getObjectArray()` gives us direct access to this internal array structure.

#### Implementation Pattern:

```java
// Extract as array
Object[] filterArray = MagikVectorUtils.getObjectArray(magikFilters);

// Iterate in pairs (key-value alternating)
for (int i = 0; i < filterArray.length - 1; i += 2) {
    Object keyObj = filterArray[i];      // :latest_status_name
    Object valueObj = filterArray[i + 1]; // "in_progress"

    // Process key (remove leading :)
    String keyStr = keyObj.toString();
    if (keyStr.startsWith(":")) {
        keyStr = keyStr.substring(1);
    }

    // Extract value as string
    String stringValue = MagikInteropUtils.fromMagikString(valueObj);

    // Build query string
    params.append(keyStr).append("=").append(URLEncoder.encode(stringValue, "UTF-8"));
}
```

## Alternative Approaches Investigated

### 1. ❌ Reflection with `at()` method
**Status:** Failed - `at()` method doesn't exist
**Issue:** property_list uses `at0()` not `at()`

### 2. ❌ Map interface casting
**Status:** May work but unreliable
**Issue:** property_list might not implement Java Map interface consistently

### 3. ✅ Array-based extraction (Current Implementation)
**Status:** Correct approach
**Advantages:**
- Direct access to internal structure
- No reflection needed
- Efficient and reliable
- Matches Magik's actual data structure

## Current Implementation Status

**File:** `AstriWorkOrderProcs.java`
**Method:** `buildFilterParams()`
**Line:** 268-339

**Strategy:**
1. **Primary:** Try array-based extraction using `getObjectArray()`
2. **Fallback:** Try Map interface or reflection (for edge cases)

**Result:** This is the optimal solution given available Magik interop utilities.

## Verification Command

To verify MagikVectorUtils methods:
```bash
javap -public -cp C:\Smallworld\core\libs\com.gesmallworld.magik.interop-5.3.6.0-412.jar com.gesmallworld.magik.interop.MagikVectorUtils
```

To verify MagikInteropUtils methods:
```bash
javap -public -cp C:\Smallworld\core\libs\com.gesmallworld.magik.interop-5.3.6.0-412.jar com.gesmallworld.magik.interop.MagikInteropUtils
```

## References

- **Smallworld Version:** 5.3.6.0-412
- **Interop JAR:** `com.gesmallworld.magik.interop-5.3.6.0-412.jar`
- **Commons JAR:** `com.gesmallworld.magik.commons-5.3.6.0-412.jar`

---

**Conclusion:** Array-based extraction using `MagikVectorUtils.getObjectArray()` is the **only** and **best** approach for accessing property_list data from Java. There is no `getObjectMap()` method available.
