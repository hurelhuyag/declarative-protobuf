# Agent rules

- **Never commit unless asked explicitly.** Leave changes in the working tree and say what is ready to commit.
  "Fix", "make it pass" or a green build is not a request to commit. Only a message that literally asks for a
  commit (or a push) counts, and it covers that one commit only.
- **Never add `Co-Authored-By` or any other attribution trailer** to commit messages, PR descriptions or anything
  else unless asked explicitly. The commit is the author's; the tool used to write it is not mentioned.

# Code style

- **Constructors over builders.** Create objects with `new Type(...)`; do not introduce or reach for a builder
  when a constructor exists.
- **Inline single-use helper methods.** A method with one caller is written at the call site, however long it
  gets. Only a method called from two or more places earns a name.
- **Inline single-use variables and constants.** A local, field or constant read once is replaced by its
  expression at that one place.
- **Tests are independent.** Each test declares its own data: no shared seed constants, fixtures or records that
  several tests read. Duplicating setup between tests is expected.
