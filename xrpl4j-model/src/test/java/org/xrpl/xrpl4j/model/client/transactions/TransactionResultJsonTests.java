package org.xrpl.xrpl4j.model.client.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import org.json.JSONException;
import org.junit.Test;
import org.xrpl.xrpl4j.model.AbstractJsonTest;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.Flags;
import org.xrpl.xrpl4j.model.transactions.Hash256;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;

public class TransactionResultJsonTests extends AbstractJsonTest {

  @Test
  public void testPaymentTransactionResultJson() throws JsonProcessingException, JSONException {
    TransactionResult<Payment> paymentResult = TransactionResult.<Payment>builder()
        .hash(Hash256.of("E939C30F233E3E6B0A9F829BDDA258CB9DA38D11C0F66C7D60E38B9D9FA987B8"))
        .transaction(Payment.builder()
            .account(Address.of("rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe"))
            .amount(XrpCurrencyAmount.of(UnsignedLong.valueOf(1000000000)))
            .destination(Address.of("r3ubyDp4gPGKH5bJx9KMmzpTSTW7EtRixS"))
            .fee(XrpCurrencyAmount.of(UnsignedLong.valueOf(12)))
            .flags(Flags.PaymentFlags.of(2147483648L))
            .lastLedgerSequence(UnsignedInteger.valueOf(13010048))
            .sequence(UnsignedInteger.valueOf(2062126))
            .signingPublicKey("02356E89059A75438887F9FEE2056A2890DB82A68353BE9C0C0C8F89C0018B37FC")
            .transactionSignature("3045022100AA15E1F82455712B7D3CE138F6B913238CFBFF56DCB3E2DE39624EE4C639F190022003A04CE739D93DF23BB7F646E274191F550AC73975737FA5436BCF8FEF29E4DD")
            .build())
        .build();

    String json = "{\n" +
        "                    \"Account\": \"rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe\",\n" +
        "                    \"Amount\": \"1000000000\",\n" +
        "                    \"Destination\": \"r3ubyDp4gPGKH5bJx9KMmzpTSTW7EtRixS\",\n" +
        "                    \"Fee\": \"12\",\n" +
        "                    \"Flags\": 2147483648,\n" +
        "                    \"LastLedgerSequence\": 13010048,\n" +
        "                    \"Sequence\": 2062126,\n" +
        "                    \"SigningPubKey\": \"02356E89059A75438887F9FEE2056A2890DB82A68353BE9C0C0C8F89C0018B37FC\",\n" +
        "                    \"TransactionType\": \"Payment\",\n" +
        "                    \"TxnSignature\": \"3045022100AA15E1F82455712B7D3CE138F6B913238CFBFF56DCB3E2DE39624EE4C639F190022003A04CE739D93DF23BB7F646E274191F550AC73975737FA5436BCF8FEF29E4DD\",\n" +
        "                    \"validated\": false,\n" +
        "                    \"hash\": \"E939C30F233E3E6B0A9F829BDDA258CB9DA38D11C0F66C7D60E38B9D9FA987B8\"\n" +
        "                }";

    assertCanSerializeAndDeserialize(paymentResult, json);
  }
}
