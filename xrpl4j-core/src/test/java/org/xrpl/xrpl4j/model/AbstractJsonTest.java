package org.xrpl.xrpl4j.model;

/*-
 * ========================LICENSE_START=================================
 * xrpl4j :: model
 * %%
 * Copyright (C) 2020 - 2022 XRPL Foundation and its contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xrpl.xrpl4j.model.client.XrplRequestParams;
import org.xrpl.xrpl4j.model.client.XrplResult;
import org.xrpl.xrpl4j.model.client.serverinfo.ServerInfo.ValidatedLedger;
import org.xrpl.xrpl4j.model.jackson.ObjectMapperFactory;
import org.xrpl.xrpl4j.model.ledger.LedgerObject;
import org.xrpl.xrpl4j.model.transactions.Transaction;

public class AbstractJsonTest {

  protected Logger logger = LoggerFactory.getLogger(this.getClass());
  protected ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() {
    objectMapper = ObjectMapperFactory.create();
  }

  protected void assertCanSerializeAndDeserialize(Transaction transaction, String json)
    throws JsonProcessingException, JSONException {
    String serialized = objectMapper.writeValueAsString(transaction);
    JSONAssert.assertEquals(json, serialized, JSONCompareMode.STRICT);

    Transaction deserialized = objectMapper.readValue(serialized, Transaction.class);
    assertThat(deserialized).isEqualTo(transaction);
  }

  protected void assertCanSerializeAndDeserialize(XrplResult result, String json)
    throws JsonProcessingException, JSONException {
    String serialized = objectMapper.writeValueAsString(result);
    JSONAssert.assertEquals(json, serialized, JSONCompareMode.STRICT);

    XrplResult deserialized = objectMapper.readValue(serialized, result.getClass());
    assertThat(deserialized).isEqualTo(result);
  }

  protected void assertCanSerializeAndDeserialize(XrplRequestParams params, String json)
    throws JsonProcessingException, JSONException {
    String serialized = objectMapper.writeValueAsString(params);
    JSONAssert.assertEquals(json, serialized, JSONCompareMode.STRICT);

    XrplRequestParams deserialized = objectMapper.readValue(serialized, params.getClass());
    assertThat(deserialized).isEqualTo(params);
  }

  protected void assertCanSerializeAndDeserialize(LedgerObject ledgerObject, String json)
    throws JsonProcessingException, JSONException {
    String serialized = objectMapper.writeValueAsString(ledgerObject);
    JSONAssert.assertEquals(json, serialized, JSONCompareMode.STRICT);

    LedgerObject deserialized = objectMapper.readValue(serialized, LedgerObject.class);
    assertThat(deserialized).isEqualTo(ledgerObject);
  }

  protected void assertCanSerializeAndDeserialize(
    ValidatedLedger serverInfoLedger, String json
  )
    throws JsonProcessingException, JSONException {
    String serialized = objectMapper.writeValueAsString(serverInfoLedger);
    JSONAssert.assertEquals(json, serialized, JSONCompareMode.STRICT);

    ValidatedLedger deserialized = objectMapper.readValue(
      serialized, ValidatedLedger.class
    );
    assertThat(deserialized).isEqualTo(serverInfoLedger);
  }

  protected void assertCanDeserialize(String json, XrplResult result) throws JsonProcessingException {
    XrplResult deserialized = objectMapper.readValue(json, result.getClass());
    assertThat(deserialized).isEqualTo(result);
  }
}
