package tools.xyzzy.fixdecoder;

import java.util.Map;
import java.util.TreeMap;

/**
 * Sensitive FIX tags generated from the QuickFIX field names used by the Rust project.
 */
final class SensitiveTags {
    private SensitiveTags() {
    }

    /** Returns tag-to-alias-prefix mappings for obfuscation. */
    static Map<Integer, String> names() {
        Map<Integer, String> map = new TreeMap<>();
        map.put(1, "Account");
        map.put(49, "SenderCompID");
        map.put(50, "SenderSubID");
        map.put(56, "TargetCompID");
        map.put(57, "TargetSubID");
        map.put(79, "AllocAccount");
        map.put(115, "OnBehalfOfCompID");
        map.put(116, "OnBehalfOfSubID");
        map.put(128, "DeliverToCompID");
        map.put(129, "DeliverToSubID");
        map.put(142, "SenderLocationID");
        map.put(143, "TargetLocationID");
        map.put(144, "OnBehalfOfLocationID");
        map.put(145, "DeliverToLocationID");
        map.put(283, "LocationID");
        map.put(440, "ClearingAccount");
        map.put(523, "PartySubID");
        map.put(545, "NestedPartySubID");
        map.put(553, "Username");
        map.put(554, "Password");
        map.put(581, "AccountType");
        map.put(625, "TradingSessionSubID");
        map.put(628, "HopCompID");
        map.put(671, "LegAllocAccount");
        map.put(717, "SettlSessSubID");
        map.put(760, "Nested2PartySubID");
        map.put(785, "SettlPartySubID");
        map.put(786, "SettlPartySubIDType");
        map.put(798, "AllocAccountType");
        map.put(801, "NoSettlPartySubIDs");
        map.put(802, "NoPartySubIDs");
        map.put(803, "PartySubIDType");
        map.put(804, "NoNestedPartySubIDs");
        map.put(805, "NestedPartySubIDType");
        map.put(806, "NoNested2PartySubIDs");
        map.put(807, "Nested2PartySubIDType");
        map.put(809, "NoUsernames");
        map.put(823, "UnderlyingTradingSessionSubID");
        map.put(925, "NewPassword");
        map.put(930, "RefCompID");
        map.put(931, "RefSubID");
        map.put(936, "NoCompIDs");
        map.put(952, "NoNested3PartySubIDs");
        map.put(953, "Nested3PartySubID");
        map.put(954, "Nested3PartySubIDType");
        map.put(1052, "NoInstrumentPartySubIDs");
        map.put(1053, "InstrumentPartySubID");
        map.put(1054, "InstrumentPartySubIDType");
        map.put(1062, "NoUndlyInstrumentPartySubIDs");
        map.put(1063, "UndlyInstrumentPartySubID");
        map.put(1064, "UndlyInstrumentPartySubIDType");
        map.put(1114, "TriggerTradingSessionSubID");
        map.put(1120, "NoRootPartySubIDs");
        map.put(1121, "RootPartySubID");
        map.put(1122, "RootPartySubIDType");
        map.put(1296, "NoDerivativeInstrumentPartySubIDs");
        map.put(1297, "DerivativeInstrumentPartySubID");
        map.put(1298, "DerivativeInstrumentPartySubIDType");
        map.put(1400, "EncryptedPasswordMethod");
        map.put(1401, "EncryptedPasswordLen");
        map.put(1402, "EncryptedPassword");
        map.put(1403, "EncryptedNewPasswordLen");
        map.put(1404, "EncryptedNewPassword");
        map.put(1411, "Nested4PartySubIDType");
        map.put(1412, "Nested4PartySubID");
        map.put(1413, "NoNested4PartySubIDs");
        map.put(1519, "NoPartyDetailAltSubIDs");
        map.put(1520, "PartyDetailAltSubID");
        map.put(1521, "PartyDetailAltSubIDType");
        map.put(1566, "NoRelatedPartyDetailSubIDs");
        map.put(1567, "RelatedPartyDetailSubID");
        map.put(1568, "RelatedPartyDetailSubIDType");
        map.put(1572, "NoRelatedPartyDetailAltSubIDs");
        map.put(1573, "RelatedPartyDetailAltSubID");
        map.put(1574, "RelatedPartyDetailAltSubIDType");
        map.put(1661, "NoRequestingPartySubIDs");
        map.put(1662, "RequestingPartySubID");
        map.put(1663, "RequestingPartySubIDType");
        map.put(1694, "NoPartyDetailSubIDs");
        map.put(1695, "PartyDetailSubID");
        map.put(1696, "PartyDetailSubIDType");
        map.put(1699, "AccountSummaryReportID");
        map.put(1816, "ClearingAccountType");
        map.put(1817, "LegClearingAccountType");
        map.put(1891, "TrdMatchSubID");
        map.put(1918, "NoClearingAccountTypes");
        map.put(2258, "NoLegInstrumentPartySubIDs");
        map.put(2259, "LegInstrumentPartySubID");
        map.put(2260, "LegInstrumentPartySubIDType");
        map.put(2433, "NoTargetPartySubIDs");
        map.put(2434, "TargetPartySubID");
        map.put(2435, "TargetPartySubIDType");
        map.put(2567, "PrimaryServiceLocationID");
        map.put(2568, "SecondaryServiceLocationID");
        map.put(2674, "FillMatchSubID");
        map.put(2680, "LegAccount");
        map.put(2816, "PostTradePaymentAccount");
        map.put(40178, "NoProvisionPartySubIDs");
        map.put(40179, "ProvisionPartySubID");
        map.put(40180, "ProvisionPartySubIDType");
        map.put(40238, "NoPaymentSettlPartySubIDs");
        map.put(40239, "PaymentSettlPartySubID");
        map.put(40240, "PaymentSettlPartySubIDType");
        map.put(40537, "NoLegProvisionPartySubIDs");
        map.put(40538, "LegProvisionPartySubID");
        map.put(40539, "LegProvisionPartySubIDType");
        map.put(42177, "NoUnderlyingProvisionPartySubIDs");
        map.put(42178, "UnderlyingProvisionPartySubID");
        map.put(42179, "UnderlyingProvisionPartySubIDType");
        return Map.copyOf(map);
    }
}
