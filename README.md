# Zpeedy Script

Zpeedy is an indentation-based descriptive language that compiles through
`ZPECompilerBytecodeBuilder` and executes on the ZPE runtime.

```zpeedy
set numbers to [1, 2, 3]

for every number in numbers
  display number

set x to 0
while x is less than 3 do
  display x
  set x to x plus 1

repeat 2 times
  display "Again"

repeat forever
  display "Once"
  stop loop

when x
  is 1 then
    display "One"
  is 2 then
    display "Two"
  otherwise
    display "Something else"

set instruction to choice based on colour
  "red" gives "Stop"
  "amber" gives "Wait"
  otherwise gives "Unknown"

display instruction

if x is at least 10 then
  display "Ten or more"
alternatively if x is at least 5 then
  display "Five or more"
otherwise
  display "Less than five"

attempt
  display riskyValue
otherwise on error message
  display message

routine displayGreeting takes name
  display name

routine double takes number
  give back number * 2

call displayGreeting with "Jamie"
set result to call double with 10

thing Person
  has name
  has age

jamie is Person with
  name as "Jamie"
  age as 34
```

Run a source file with:

```text
zpeedy -r program.zps
```

Install the packaged JAR and the `zpeedy` command with:

```text
java -jar zpeedy.jar --install
java -jar zpeedy.jar --install -memory 2048
```

The JAR is installed in Zpeedy's platform application-data directory under
`jamiebalfour/zpeedy`, independently of ZPE's `jamiebalfour/zpe` directory.

Indentation uses spaces. Tabs are rejected. `display` accepts exactly one value.
Comments begin with `#` and continue to the end of the line.

Zpeedy uses `nothing` for ZPE's null value and `unknown` for its undefined value.
Arithmetic accepts either readable words (`plus`, `minus`, `times`, `divide`) or
the equivalent symbols (`+`, `-`, `*`, `/`).

Zpeedy calls structures `thing`s. Instances are declared directly with `name is
Thing`, without `set`; `with` introduces indented named property initialisers. Objects
can only be created from declared things—Zpeedy does not support anonymous object literals.
