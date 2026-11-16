package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarControlRulesTest {
    @Test
    fun `tab counter shows exact count through nine hundred ninety nine`() {
        assertEquals("0", AddressBarControlRules.tabCountLabel(0))
        assertEquals("1", AddressBarControlRules.tabCountLabel(1))
        assertEquals("99", AddressBarControlRules.tabCountLabel(99))
        assertEquals("100", AddressBarControlRules.tabCountLabel(100))
        assertEquals("999", AddressBarControlRules.tabCountLabel(999))
    }

    @Test
    fun `tab counter uses infinity above nine hundred ninety nine`() {
        assertEquals("∞", AddressBarControlRules.tabCountLabel(1000))
        assertEquals("∞", AddressBarControlRules.tabCountLabel(5000))
    }

    @Test
    fun `editor uses full width only with focus and visible ime`() {
        assertTrue(
            AddressBarControlRules.editorUsesFullWidth(
                editing = true,
                addressFieldFocused = true,
                imeVisible = true,
            ),
        )
        assertFalse(
            AddressBarControlRules.editorUsesFullWidth(
                editing = true,
                addressFieldFocused = true,
                imeVisible = false,
            ),
        )
        assertFalse(
            AddressBarControlRules.editorUsesFullWidth(
                editing = true,
                addressFieldFocused = false,
                imeVisible = true,
            ),
        )
        assertFalse(
            AddressBarControlRules.editorUsesFullWidth(
                editing = false,
                addressFieldFocused = true,
                imeVisible = true,
            ),
        )
    }

    @Test
    fun `editor starts full width when the bar was hidden before editing`() {
        assertTrue(
            AddressBarControlRules.editorUsesFullWidth(
                editing = true,
                addressFieldFocused = false,
                imeVisible = false,
                startsFullWidth = true,
            ),
        )
        assertFalse(
            AddressBarControlRules.editorUsesFullWidth(
                editing = false,
                addressFieldFocused = false,
                imeVisible = false,
                startsFullWidth = true,
            ),
        )
    }
}
