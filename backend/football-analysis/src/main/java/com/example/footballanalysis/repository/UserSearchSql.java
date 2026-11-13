package com.example.footballanalysis.repository;

/**
 * A felhasználókeresés közös SQL-feltétele a globális listához és a klubtagok listájához.
 *
 * <p>A teljes névre mindkét sorrendben keres („Kovács Péter” és „Péter Kovács”), mert a felület a nyelvtől
 * függően így írja ki a neveket; a teljes név a vezeték- és keresztnévre keresést is lefedi. Mellette az e-mailre
 * keres. A {@code :pattern} kisbetűs, {@code %…%} közé tett, escape-elt minta; üres keresésnél {@code %}, ami
 * minden sorra illik, mert a teljes név sosem NULL.
 *
 * <p>A V15 migráció trigram indexei pontosan ezekre a kifejezésekre épülnek. Ha itt változik egy kifejezés,
 * az indexet is át kell írni, különben a Postgres nem használja.
 */
final class UserSearchSql {

    static final String NAME_OR_EMAIL_MATCH = """
            (
                lower(coalesce(u.last_name, '') || ' ' || coalesce(u.first_name, '')) LIKE :pattern ESCAPE '\\'
                OR lower(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) LIKE :pattern ESCAPE '\\'
                OR lower(u.email) LIKE :pattern ESCAPE '\\'
            )
            """;

    private UserSearchSql() {
    }
}
