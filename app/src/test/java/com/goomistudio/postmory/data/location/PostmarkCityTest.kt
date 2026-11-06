package com.goomistudio.postmory.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

class PostmarkCityTest {
    @Test fun indonesiaUsesTheCityNotTheDistrict() {
        assertEquals("Tangerang", PostmarkCity.from("Kelapa Dua", "Kabupaten Tangerang", "Banten", "ID"))
        assertEquals("Tangerang", PostmarkCity.from("Kelapa Dua", "Tangerang Regency", "Banten", "ID"))
        assertEquals("Bandung", PostmarkCity.from("Coblong", "Kota Bandung", "Jawa Barat", "ID"))
        assertEquals("Jakarta Selatan", PostmarkCity.from("Kebayoran Baru", "Kota Administrasi Jakarta Selatan", "DKI Jakarta", "ID"))
        assertEquals("Surabaya", PostmarkCity.from("Gubeng", "Surabaya City", "Jawa Timur", "id"))
    }

    @Test fun indonesiaWithoutSubAdminKeepsTheLocality() {
        assertEquals("Kelapa Dua", PostmarkCity.from("Kelapa Dua", null, "Banten", "ID"))
        assertEquals("Kelapa Dua", PostmarkCity.from("Kelapa Dua", "Kabupaten", "Banten", "ID"))
    }

    @Test fun otherCountriesKeepTheLocality() {
        assertEquals("Kyoto", PostmarkCity.from("Kyoto", "Kyoto City", "Kyoto", "JP"))
        assertEquals("Paris", PostmarkCity.from("Paris", "Paris", "Île-de-France", "FR"))
        assertEquals("Kent", PostmarkCity.from(null, "Kent", "England", "GB"))
        assertEquals("Bali", PostmarkCity.from(null, null, "Bali", "US"))
        assertEquals(null, PostmarkCity.from(null, " ", null, "US"))
    }
}
