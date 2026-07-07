package com.mirth.connect.plugins.dynamiclookup.server.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class LookupExceptionsTest {

    // DuplicateGroupNameException
    @Test
    public void duplicateGroupName_message() {
        DuplicateGroupNameException ex = new DuplicateGroupNameException("dup");
        assertEquals("dup", ex.getMessage());
    }

    @Test(expected = DuplicateGroupNameException.class)
    public void duplicateGroupName_throwCatch() {
        throw new DuplicateGroupNameException("dup");
    }

    // GroupNotFoundException
    @Test
    public void groupNotFound_message() {
        GroupNotFoundException ex = new GroupNotFoundException("nf");
        assertEquals("nf", ex.getMessage());
    }

    @Test(expected = GroupNotFoundException.class)
    public void groupNotFound_throwCatch() {
        throw new GroupNotFoundException("nf");
    }

    // InvalidFilterException (2 constructors)
    @Test
    public void invalidFilter_messageOnly() {
        InvalidFilterException ex = new InvalidFilterException("bad");
        assertEquals("bad", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void invalidFilter_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        InvalidFilterException ex = new InvalidFilterException("bad", cause);
        assertEquals("bad", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test(expected = InvalidFilterException.class)
    public void invalidFilter_throwCatch() {
        throw new InvalidFilterException("bad");
    }

    // LookupTableException (checked)
    @Test
    public void lookupTable_message() throws Exception {
        LookupTableException ex = new LookupTableException("tbl");
        assertEquals("tbl", ex.getMessage());
    }

    @Test
    public void lookupTable_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        LookupTableException ex = new LookupTableException("tbl", cause);
        assertEquals("tbl", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    public void lookupTable_causeOnly() {
        RuntimeException cause = new RuntimeException("root");
        LookupTableException ex = new LookupTableException(cause);
        assertSame(cause, ex.getCause());
    }

    // ValueOperationException
    @Test
    public void valueOperation_message() {
        ValueOperationException ex = new ValueOperationException("op");
        assertEquals("op", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void valueOperation_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        ValueOperationException ex = new ValueOperationException("op", cause);
        assertEquals("op", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test(expected = ValueOperationException.class)
    public void valueOperation_throwCatch() {
        throw new ValueOperationException("op");
    }

    // ValueTableCreationException
    @Test
    public void valueTableCreation_message() {
        ValueTableCreationException ex = new ValueTableCreationException("vtc");
        assertEquals("vtc", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void valueTableCreation_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        ValueTableCreationException ex = new ValueTableCreationException("vtc", cause);
        assertEquals("vtc", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test(expected = ValueTableCreationException.class)
    public void valueTableCreation_throwCatch() {
        throw new ValueTableCreationException("vtc");
    }
}
