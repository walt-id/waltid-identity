`linearized.pdf` is our unsigned "Original approved content" test PDF, linearized with qpdf 12.4.2.
It contains no signatures, keys or external test data. qpdf is only used to prepare this fixture and
is not needed to build or run the tests. It exercises a compressed-object/xref-stream base file
whose first xref section points forward to the main section of the same linearized revision.
