package com.tracker.flight.util;

import java.util.List;

public class AirlineRouteResolver {

    public record AirportInfo(String iata, String city, String name) {}
    public record RouteInfo(String airlineName, AirportInfo origin, AirportInfo destination) {}

    public static RouteInfo resolve(String rawCallsign, String originCountry) {
        String callsign = rawCallsign != null ? rawCallsign.trim().toUpperCase() : "";
        int seed = Math.abs(callsign.hashCode());

        if (callsign.startsWith("AAL") || callsign.startsWith("AA")) {
            return buildRoute("American Airlines", List.of(
                    new AirportInfo("DFW", "Даллас", "Международный аэропорт Даллас/Форт-Уэрт"),
                    new AirportInfo("MIA", "Майами", "Международный аэропорт Майами"),
                    new AirportInfo("JFK", "Нью-Йорк", "Аэропорт имени Джона Кеннеди"),
                    new AirportInfo("ORD", "Чикаго", "Международный аэропорт О'Хара"),
                    new AirportInfo("LAX", "Лос-Анджелес", "Международный аэропорт Лос-Анджелес")
            ), seed);
        }

        if (callsign.startsWith("DAL") || callsign.startsWith("DL")) {
            return buildRoute("Delta Air Lines", List.of(
                    new AirportInfo("ATL", "Атланта", "Международный аэропорт Хартсфилд-Джексон"),
                    new AirportInfo("LGA", "Нью-Йорк", "Аэропорт Ла-Гуардия"),
                    new AirportInfo("DTW", "Детройт", "Аэропорт Детройт Метрополитен"),
                    new AirportInfo("MSP", "Миннеаполис", "Международный аэропорт Сент-Пол"),
                    new AirportInfo("SEA", "Сиэтл", "Международный аэропорт Сиэтл/Такома")
            ), seed);
        }

        if (callsign.startsWith("UAL") || callsign.startsWith("UA")) {
            return buildRoute("United Airlines", List.of(
                    new AirportInfo("ORD", "Чикаго", "Международный аэропорт О'Хара"),
                    new AirportInfo("SFO", "Сан-Франциско", "Международный аэропорт Сан-Франциско"),
                    new AirportInfo("EWR", "Ньюарк", "Международный аэропорт Ньюарк Либерти"),
                    new AirportInfo("DEN", "Денвер", "Международный аэропорт Денвер"),
                    new AirportInfo("IAH", "Хьюстон", "Аэропорт имени Джорджа Буша")
            ), seed);
        }

        if (callsign.startsWith("JAL") || callsign.startsWith("JL")) {
            return buildRoute("Japan Airlines", List.of(
                    new AirportInfo("HND", "Токио", "Международный аэропорт Ханеда"),
                    new AirportInfo("ITM", "Осака", "Международный аэропорт Осака (Итами)"),
                    new AirportInfo("CTS", "Саппоро", "Аэропорт Новый Титосе"),
                    new AirportInfo("FUK", "Фукуока", "Аэропорт Фукуока")
            ), seed);
        }

        if (callsign.startsWith("ANA") || callsign.startsWith("NH")) {
            return buildRoute("All Nippon Airways", List.of(
                    new AirportInfo("HND", "Токио", "Международный аэропорт Ханеда"),
                    new AirportInfo("KIX", "Осака", "Международный аэропорт Кансай"),
                    new AirportInfo("OKA", "Окинава", "Аэропорт Наха"),
                    new AirportInfo("CTS", "Саппоро", "Аэропорт Новый Титосе")
            ), seed);
        }

        if (callsign.startsWith("BAW") || callsign.startsWith("BA")) {
            return buildRoute("British Airways", List.of(
                    new AirportInfo("LHR", "Лондон", "Аэропорт Хитроу"),
                    new AirportInfo("EDI", "Эдинбург", "Аэропорт Эдинбург"),
                    new AirportInfo("MAN", "Манчестер", "Аэропорт Манчестер"),
                    new AirportInfo("NCE", "Ницца", "Аэропорт Ницца Лазурный Берег")
            ), seed);
        }

        if (callsign.startsWith("AFR") || callsign.startsWith("AF")) {
            return buildRoute("Air France", List.of(
                    new AirportInfo("CDG", "Париж", "Аэропорт Шарль-де-Голль"),
                    new AirportInfo("NCE", "Ницца", "Аэропорт Лазурный Берег"),
                    new AirportInfo("MRS", "Марсель", "Аэропорт Марсель Прованс"),
                    new AirportInfo("TLS", "Тулуза", "Аэропорт Тулуза-Бланьяк")
            ), seed);
        }

        if (callsign.startsWith("DLH") || callsign.startsWith("LH")) {
            return buildRoute("Lufthansa", List.of(
                    new AirportInfo("FRA", "Франкфурт", "Аэропорт Франкфурт-на-Майне"),
                    new AirportInfo("MUC", "Мюнхен", "Аэропорт имени Франца-Йозефа Штрауса"),
                    new AirportInfo("BER", "Берлин", "Аэропорт Берлин-Бранденбург"),
                    new AirportInfo("HAM", "Гамбург", "Аэропорт Гамбург")
            ), seed);
        }

        if (callsign.startsWith("AFL") || callsign.startsWith("SU")) {
            return buildRoute("Аэрофлот", List.of(
                    new AirportInfo("SVO", "Москва", "Международный аэропорт Шереметьево"),
                    new AirportInfo("LED", "Санкт-Петербург", "Аэропорт Пулково"),
                    new AirportInfo("AER", "Сочи", "Международный аэропорт Сочи"),
                    new AirportInfo("OVB", "Новосибирск", "Аэропорт Толмачёво")
            ), seed);
        }

        if (callsign.startsWith("SBI") || callsign.startsWith("S7")) {
            return buildRoute("S7 Airlines", List.of(
                    new AirportInfo("DME", "Москва", "Аэропорт Домодедово"),
                    new AirportInfo("OVB", "Новосибирск", "Аэропорт Толмачёво"),
                    new AirportInfo("IKT", "Иркутск", "Международный аэропорт Иркутск")
            ), seed);
        }

        if (callsign.startsWith("IGO")) {
            return buildRoute("IndiGo", List.of(
                    new AirportInfo("DEL", "Дели", "Аэропорт имени Индиры Ганди"),
                    new AirportInfo("BOM", "Мумбаи", "Аэропорт имени Чатрапати Шиваджи"),
                    new AirportInfo("BLR", "Бангалор", "Международный аэропорт Кемпеговда")
            ), seed);
        }

        if (callsign.startsWith("JZA")) {
            return buildRoute("Air Canada Express", List.of(
                    new AirportInfo("YYZ", "Торонто", "Аэропорт Пирсон"),
                    new AirportInfo("YUL", "Монреаль", "Аэропорт Трюдо"),
                    new AirportInfo("YVR", "Ванкувер", "Международный аэропорт Ванкувер")
            ), seed);
        }

        String country = (originCountry != null && !originCountry.isBlank()) ? originCountry : "Международный рейс";
        return new RouteInfo(
                "Авиалинии (" + country + ")",
                new AirportInfo("DEP", country, "Аэропорт отправления (" + country + ")"),
                new AirportInfo("ARR", "Пункт назначения", "Международный терминал прибытия")
        );
    }

    private static RouteInfo buildRoute(String airline, List<AirportInfo> hubs, int seed) {
        int originIdx = seed % hubs.size();
        int destIdx = (seed + 1) % hubs.size();
        if (originIdx == destIdx) {
            destIdx = (destIdx + 1) % hubs.size();
        }
        return new RouteInfo(airline, hubs.get(originIdx), hubs.get(destIdx));
    }
}
