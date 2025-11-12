# Magik Language Reference Guide

## Overview
This document provides a comprehensive reference for the Magik programming language used in Smallworld GIS development. It is derived from official Smallworld training materials and should be used as the primary reference when writing Magik code.

## Table of Contents
1. [Starting a Magik Session](#starting-a-magik-session)
2. [Emacs Integration](#emacs-integration)
3. [Basic Syntax](#basic-syntax)
4. [Conditionals and Blocks](#conditionals-and-blocks)
5. [Methods and Procedures](#methods-and-procedures)
6. [Best Practices](#best-practices)
7. [Common Patterns](#common-patterns)

---

## Starting a Magik Session

### Command Line
```bash
sw_magik_win32 -image images\swaf.msf -Mextdir C:\temp -cli
```

### Interactive Environment
- Magik prompt: `MagikSF>`
- Type code directly at the prompt for testing
- Use `$` to force evaluation if the system gets confused
- Last result is always assigned to global `!`

---

## Emacs Integration

### Useful Emacs Commands

#### Navigation
- `Ctrl+A` - Move cursor to beginning of line
- `Ctrl+E` - Move cursor to end of line
- Arrow keys and mouse for standard navigation

#### Window Management
- `Ctrl+X, 2` - Split frame into two windows
- `Ctrl+X, O` - Switch to other buffer
- `Ctrl+X, Ctrl+F` - Create or load file
- `Ctrl+X, S` - Save buffer to file
- `F2, Z` - Make buffer *gis*

#### Magik-Specific Commands
- `Alt+P` - Retrieve previous command
- `Alt+N` - Retrieve next command
- `F2, P` - Retrieve previous command matching current string
- `F2, N` - Retrieve next command matching current string
- `F8` - Execute command directly
- `F7` - Compile thing to *gis* (code surrounding cursor)
- `F2, M` - Compile method (looks for _method/_endmethod)

#### Magik Syntax Features
- Emacs automatically prefixes underscores to Magik keywords
- Automatically appends terminator `$` on newline after complete statements
- Watch mini-buffer for syntax completion hints

---

## Basic Syntax

### File Header
All Magik source files must start with:
```magik
#% text_encoding = iso8859_1
_package sw
$
```

### Variables and Assignment

#### Global Variables
```magik
# System prompts to create global if it doesn't exist
my_string << "comparing"

# Explicitly declare global
_global aa << 1

# Declare global constant
_global _constant cc << 10
cc << 22  # ERROR: Cannot reassign constant
```

#### Local Variables
```magik
_local my_var << "value"
```

#### Multiple Assignment
```magik
(a, b) << (6, 7)      # Assign multiple values
(a, b) << (b, a)      # Swap values
```

### Naming Conventions
- Cannot start variable names with certain characters (e.g., `2str` is invalid)
- Use underscores for multi-word names: `my_variable`
- Question mark suffix for Boolean methods: `palindrome?`
- Empty brackets for methods that modify receiver: `reverse()`

### Packages
```magik
!current_package!     # Check current package
_package sw           # Switch to sw package
_package user         # Switch to user package
user:aa               # Access variable with full package name
sw:aa                 # Access variable from sw package
```

### Comments
```magik
## Documentation comment (use for methods)
# Regular comment
```

### Operators

#### Assignment Operators
```magik
a << 5                # Assignment
a +<< 3               # Add and assign (a = a + 3)
a -<< 2               # Subtract and assign (a = a - 2)
```

#### Comparison Operators
```magik
a = b                 # Equality (value comparison)
a <> b                # Inequality
a _is b               # Identity (same object)
a _isnt b             # Not identical
a > b                 # Greater than
a < b                 # Less than
a >= b                # Greater than or equal
a <= b                # Less than or equal
```

**Note:** All comparison operators (>, >=, <, <=) are internally translated to the `_cf` operator.

#### Logical Operators
```magik
_and                  # Logical AND (evaluates both sides)
_andif                # Short-circuit AND (stops if first is false)
_or                   # Logical OR (evaluates both sides)
_orif                 # Short-circuit OR (stops if first is true)
_not                  # Logical NOT
```

**Important:** Use `_andif` and `_orif` to prevent errors:
```magik
# WRONG - will error if name.size <= 8
name.size > 8 _and name[9] _is %g

# CORRECT - short-circuits if size <= 8
name.size > 8 _andif name[9] _is %g
```

#### Arithmetic Operators
```magik
+                     # Addition
-                     # Subtraction
*                     # Multiplication
/                     # Division (returns rational if needed)
_div                  # Integer division
_mod                  # Modulo (remainder)
```

### Data Types

#### Numbers
```magik
aa << 6/7             # Rational number
show(aa)              # Display: 6/7
bb << 5/3 * aa        # Still rational
bb * 1.6              # Coerced to real (float)
a.factorial           # Returns bignum for large values
```

#### Strings
```magik
s << "hello world"    # String (char16_vector)
s.class_name          # Returns: char16_vector
s.size                # String length
s[1]                  # First character (1-indexed)
s.lowercase           # Convert to lowercase
```

#### Characters
```magik
%a                    # Character literal
%.                    # Period character
%g                    # Character 'g'
c.digit?              # Check if character is a digit
```

#### Collections
```magik
v << {1, 2, 3}        # Simple vector
v.an_element()        # Get any element
v.size                # Number of elements
v[1]                  # First element (1-indexed)
```

---

## Conditionals and Blocks

### If Statements
```magik
_if condition
_then
    # code when true
_endif

_if condition
_then
    # code when true
_else
    # code when false
_endif

_if condition
_then
    # code
_elif another_condition
_then
    # code
_else
    # code
_endif
```

**Style Note:** Keep `_then` on a new line for better readability.

### Blocks
```magik
# Basic block
_block
    _local aa << "hello"
    show(aa)
_endblock

# Block with early exit
_block
    _if condition
    _then
        _leave _with value
    _endif
    # more code
_endblock

# Returning a value from block
result << _block
    _if aa < 6
    _then
        _leave _with 10
    _else
        _leave _with 12
    _endif
_endblock
```

**Important:** `_local` variables are scoped to the block only.

### Loops

#### For Loop with Iterator
```magik
# Loop over range
_for i _over 1.upto(10)
_loop
    write(i)
_endloop

# Loop over collection elements
_for element _over collection.fast_elements()
_loop
    # process element
_endloop

# Loop with step
_for i _over 1.upto(100).step_by(5)
_loop
    # i will be 1, 6, 11, 16, ...
_endloop
```

#### Infinite Loop with Exit
```magik
_loop
    # code
    _if exit_condition
    _then
        _leave
    _endif
_endloop
```

#### Continue Statement
```magik
_for i _over 1.upto(10)
_loop
    _if i.even?
    _then
        _continue  # Skip to next iteration
    _endif
    write(i)
_endloop
```

---

## Methods and Procedures

### Method Definition

#### Basic Method
```magik
_method class_name.method_name(param1, param2)
    ## Documentation comment describing what the method does
    ## Parameters: param1, param2
    ## Returns: description of return value

    # Method body
    result << param1 + param2
    _return result
_endmethod
$
```

**Note:** The `$` terminator is required after `_endmethod`.

#### Method with No Arguments
```magik
_method char16_vector.palindrome?
    ## Returns true if receiver is a palindrome, false otherwise
    j << _self.size
    _for i _over 1.upto(_self.size _div 2)
    _loop
        _if _self[i] <> _self[j]
        _then
            _return _false
        _endif
        j -<< 1
    _endloop
    _return _true
_endmethod
$
```

#### Method that Modifies Receiver
```magik
_method char16_vector.reverse()
    ## This method reverses its receiver in situ
    _for i _over 1.upto(_self.size _div 2)
    _loop
        j << _self.size - i + 1
        (_self[i], _self[j]) << (_self[j], _self[i])
    _endloop
    # No explicit return - returns _self by default
_endmethod
$
```

#### Method with Optional Arguments
```magik
_method simple_vector.largest(_optional supplied_proc)
    ## Returns the largest element in the vector
    ## Optional argument: supplied_proc - comparison procedure

    _if supplied_proc _isnt _unset
    _then
        cmp << supplied_proc
    _else
        cmp << _proc(e1, e2)
                   _return e1 > e2
               _endproc
    _endif

    r << _self.an_element()
    _for e _over _self.fast_elements()
    _loop
        _if cmp(e, r)
        _then
            r << e
        _endif
    _endloop
    _return r
_endmethod
$
```

#### Method Returning Multiple Values
```magik
_method simple_vector.min_and_max()
    ## Returns the minimum and maximum values of the receiver
    min << max << _self.an_element()
    _for e _over _self.fast_elements()
    _loop
        _if e > max _then max << e _endif
        _if e < min _then min << e _endif
    _endloop
    _return min, max
_endmethod
$
```

### Procedure Definition

#### Global Procedure
```magik
_global change << _proc(amount)
    ## Prints out for the argument which represents a sum of money
    ## in pence, the quantities of notes and coins necessary to
    ## make it up.

    denominations << {5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1}
    _for c _over denominations.fast_elements()
    _loop
        _if amount < c _then _continue _endif
        number << amount _div c
        amount << amount _mod c
        write("Denomination ", c, " Amount ", number)
    _endloop
_endproc
$
```

#### Inline Procedure
```magik
# Procedure as variable
my_test << _proc(element_1, element_2)
    _return element_1.write_string.lowercase >
            element_2.write_string.lowercase
_endproc

# Using the procedure
result << my_test("abc", "def")
```

#### Procedure as Argument
```magik
# Passing procedure to method
test_vector << {1, 23, "A", "x", "def"}
result << test_vector.largest(my_test)
```

### Special Keywords

#### _self
Refers to the receiver object (similar to `this` in Java/C++):
```magik
_method my_class.my_method()
    value << _self.some_slot
    _self.another_method()
_endmethod
$
```

#### _clone
Used in constructors to create a copy:
```magik
_method my_class.new(initial_data)
    >> _clone.init(initial_data)
_endmethod
$
```

#### _return vs >>
Both return values from methods/procedures:
```magik
_return value    # Explicit return
>> value         # Shorthand return (more idiomatic)
```

#### _unset
Represents an uninitialized or missing value:
```magik
_if param _is _unset
_then
    # parameter was not provided
_endif
```

#### _true and _false
Boolean constants:
```magik
_if condition
_then
    _return _true
_else
    _return _false
_endif
```

---

## Best Practices

### Naming Conventions

#### Methods
- **No arguments, no side effects**: `palindrome?` (question mark for Boolean)
- **Modifies receiver**: `reverse()` (empty brackets)
- **Has arguments**: `largest(comparison_proc)` (with brackets)

#### Variables
- Use descriptive names: `denominations`, `min_value`
- Avoid single letters except for loop counters: `i`, `j`, `e`
- Use underscores for multi-word names: `my_variable`

### Code Style

#### Indentation
```magik
_method my_class.my_method()
    _if condition
    _then
        _for i _over 1.upto(10)
        _loop
            write(i)
        _endloop
    _endif
_endmethod
$
```

#### Line Breaks
Put `_then`, `_else`, `_loop` on new lines:
```magik
# GOOD
_if value <> "9"
_and value = "99"
_then
    write("equals")
_endif

# ACCEPTABLE but less clear
_if value <> "9" _and value = "99"
_then write("equals")
_endif
```

#### Documentation
Always document methods:
```magik
_method class_name.method_name(param)
    ## Brief description of what this method does
    ##
    ## Parameters:
    ##   param - description of parameter
    ##
    ## Returns:
    ##   description of return value

    # implementation
_endmethod
$
```

### Performance Considerations

#### Use fast_elements()
```magik
# PREFERRED (faster)
_for e _over collection.fast_elements()
_loop
    # process e
_endloop

# SLOWER (unless you need the index)
_for i _over 1.upto(collection.size)
_loop
    e << collection[i]
    # process e
_endloop
```

#### Short-Circuit Operators
```magik
# Use _andif/_orif to avoid unnecessary evaluation
_if expensive_check1() _andif expensive_check2()
_then
    # expensive_check2() only runs if check1() returns true
_endif
```

#### Avoid Modifying Arguments
```magik
# BAD PRACTICE - modifies argument
_proc(amount)
    amount << amount _mod c
    # ...
_endproc

# BETTER - use local variable
_proc(amount)
    _local remaining << amount
    remaining << remaining _mod c
    # ...
_endproc
```

### Error Handling

#### Defensive Checks
```magik
_method simple_vector.first_element()
    _if _self.empty?
    _then
        _return _unset
    _endif
    _return _self[1]
_endmethod
$
```

#### Bounds Checking
```magik
# Check before accessing
_if p > _self.size _orif _not _self[p].digit?
_then
    _return _false
_endif
```

---

## Common Patterns

### Iteration Patterns

#### Process Each Element
```magik
_for element _over collection.fast_elements()
_loop
    element.do_something()
_endloop
```

#### Find Maximum/Minimum
```magik
max << collection.an_element()
_for e _over collection.fast_elements()
_loop
    _if e > max _then max << e _endif
_endloop
```

#### Filter Elements
```magik
result << rope.new()
_for e _over collection.fast_elements()
_loop
    _if e.matches_criteria?
    _then
        result.add(e)
    _endif
_endloop
```

#### Transform Elements
```magik
result << rope.new()
_for e _over collection.fast_elements()
_loop
    result.add(e.transform())
_endloop
```

### String Manipulation

#### String Comparison (Case-Insensitive)
```magik
s1.lowercase = s2.lowercase
```

#### String to Number
```magik
number << value.as_number()
```

#### Number to String
```magik
string << number.write_string
```

#### Character Checks
```magik
char.digit?          # Is it a digit?
char.letter?         # Is it a letter?
char.alphanumeric?   # Is it alphanumeric?
```

### Collection Operations

#### Check if Empty
```magik
_if collection.empty?
_then
    # handle empty case
_endif
```

#### Get Any Element
```magik
element << collection.an_element()
```

#### Collection Size
```magik
n << collection.size
```

### Polymorphism

Methods work on any object that responds to the required messages:
```magik
# Works with integers
{1, 2, 6, 5, 3}.largest  # Returns: 6

# Works with floats
{1.5, 2.7, 6.1, 5.0, 3.14159}.largest  # Returns: 6.1

# Works with strings
{"quick", "brown", "fox"}.largest  # Returns: "quick"

# Works with mixed numbers (int and float)
{1, 2.0, 6, 5, 3.14159}.largest  # Returns: 6
```

**Key Principle:** Objects don't need to be the same class, they just need to support the required operations (e.g., comparison via `_cf`).

---

## Testing and Debugging

### Interactive Testing
```magik
# Create test data
s << "abcdefg"

# Test method
s.reverse()

# Verify result
show(s)
```

### Common Built-in Methods for Testing
```magik
show(object)           # Display object details
write(object)          # Write object to output
object.class_name      # Get class name
object.write_string    # Convert to string representation
```

### Using Apropos
```magik
# Find methods containing "large"
simple_vector.apropos("large")
```

### Removing Methods
```magik
# Remove a method during development
simple_vector.remove_method(:largest)
```

---

## Customization

### Custom Magik Prompt
Create `startup.magik` in your home folder (given by `system.home`):

```magik
magik_rep.prompt_generator <<
    _proc(prompt)
        >> write_string(%(, !current_package!.name, %), prompt)
    _endproc
```

This changes the prompt from:
```
MagikSF>
```

To include the current package:
```
(user)MagikSF> _package sw
(sw)MagikSF>   y << 15
(sw)MagikSF>   _package user
(user)MagikSF> y
```

**Note:** Set environment variable `SW_MSF_STARTUP_MAGIK` to specify a custom startup file path for multiple users.

---

## Key Differences from Other Languages

### For Java/C++ Developers
- Assignment uses `<<` not `=`
- Equality uses `=` not `==`
- Identity uses `_is` not `==`
- Keywords prefixed with underscore: `_if`, `_then`, `_for`
- 1-indexed arrays (not 0-indexed)
- Statement terminator is `$` not `;`
- Method receiver is `_self` not `this`

### Dynamic Typing
```magik
a << 22              # a is an integer
a << "hello"         # now a is a string
# No type declarations needed
```

### No Build Step
- Code compiles at runtime when module loads
- Can test changes immediately by recompiling methods (F7 or F2,M)
- Interactive development at Magik prompt

---

## Additional Resources

### Documentation
- **Smallworld Class Browser** - Interactive API browser
- **Smallworld Core Class Documentation** - Complete API reference
- **Magik Language Manual** - In Application Development folder of Smallworld Core Documentation

### Quick Reference
- **GNU Emacs Guide** - Provided in training materials
- **Magik Quick Reference Card** - Key syntax and shortcuts
- **Emacs Quick Reference Card** - Essential Emacs commands

---

## Summary of Key Concepts

1. **Magik is dynamically typed** - Variables don't have fixed types
2. **Everything is an object** - Even numbers and characters
3. **Methods belong to classes** - Define methods on specific classes
4. **Polymorphism is powerful** - Methods work on any object with compatible behavior
5. **Interactive development** - Test code immediately at Magik prompt
6. **1-indexed collections** - First element is at index 1, not 0
7. **Statement terminator $** - Required after most complete statements
8. **Packages separate namespaces** - Variables exist within specific packages
9. **_self is the receiver** - Like `this` in other languages
10. **Use fast_elements() for iteration** - More efficient than indexed loops

---

## Example Code Patterns

### Complete Method Examples

#### Simple Query Method
```magik
_method char16_vector.palindrome?
    ## Returns true if receiver is a palindrome
    j << _self.size
    _for i _over 1.upto(_self.size _div 2)
    _loop
        _if _self[i] <> _self[j]
        _then
            _return _false
        _endif
        j -<< 1
    _endloop
    _return _true
_endmethod
$
```

#### Method Modifying Receiver
```magik
_method char16_vector.reverse()
    ## Reverses receiver in place
    _for i _over 1.upto(_self.size _div 2)
    _loop
        j << _self.size - i + 1
        (_self[i], _self[j]) << (_self[j], _self[i])
    _endloop
_endmethod
$
```

#### Method with Validation
```magik
_method char16_vector.is_a_number?
    ## Returns true if receiver represents a valid number
    p << 1
    # Skip sign if present
    _if _self[1] = %+ _orif _self[1] = %-
    _then
        p << 2
    _endif
    # Check for digit
    _if _not _self[p].digit?
    _then
        _return _false
    _endif
    # Loop over digits before decimal point
    _loop
        p +<< 1
        _if p > _self.size _then _return _true _endif
        _if _not _self[p].digit? _then _leave _endif
    _endloop
    # Check for decimal point
    _if _self[p] <> %.
    _then
        _return _false
    _endif
    p +<< 1
    _if p > _self.size _orif _not _self[p].digit?
    _then
        _return _false
    _endif
    # Check remaining digits
    _loop
        p +<< 1
        _if p > _self.size _then _return _true _endif
        _if _not _self[p].digit? _then _return _false _endif
    _endloop
_endmethod
$
```

#### Global Procedure
```magik
_global change << _proc(amount)
    ## Prints quantities of notes and coins to make up amount in pence
    denominations << {5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1}
    _for c _over denominations.fast_elements()
    _loop
        _if amount < c _then _continue _endif
        number << amount _div c
        amount << amount _mod c
        write("Denomination ", c, " Amount ", number)
    _endloop
_endproc
$
```

---

**Document Version:** 1.0
**Based On:** Smallworld Magik Language Training Materials (03_MagikLanguage.doc)
**Last Updated:** 2025-11-04
**Usage:** Reference this document when writing Magik code for consistency and best practices.
