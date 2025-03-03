package org.xrpl.xrpl4j.tests;

/*-
 * ========================LICENSE_START=================================
 * xrpl4j :: integration-tests
 * %%
 * Copyright (C) 2020 - 2023 XRPL Foundation and its contributors
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
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.accounts.AccountNftsResult;
import org.xrpl.xrpl4j.model.client.accounts.AccountObjectsRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountObjectsResult;
import org.xrpl.xrpl4j.model.client.accounts.NfTokenObject;
import org.xrpl.xrpl4j.model.client.fees.FeeUtils;
import org.xrpl.xrpl4j.model.client.nft.NftBuyOffersRequestParams;
import org.xrpl.xrpl4j.model.client.nft.NftBuyOffersResult;
import org.xrpl.xrpl4j.model.client.nft.NftSellOffersRequestParams;
import org.xrpl.xrpl4j.model.client.nft.NftSellOffersResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.flags.NfTokenCreateOfferFlags;
import org.xrpl.xrpl4j.model.flags.NfTokenMintFlags;
import org.xrpl.xrpl4j.model.ledger.LedgerObject;
import org.xrpl.xrpl4j.model.ledger.NfToken;
import org.xrpl.xrpl4j.model.ledger.NfTokenOfferObject;
import org.xrpl.xrpl4j.model.ledger.NfTokenPageObject;
import org.xrpl.xrpl4j.model.ledger.NfTokenWrapper;
import org.xrpl.xrpl4j.model.transactions.AccountSet;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.NfTokenAcceptOffer;
import org.xrpl.xrpl4j.model.transactions.NfTokenBurn;
import org.xrpl.xrpl4j.model.transactions.NfTokenCancelOffer;
import org.xrpl.xrpl4j.model.transactions.NfTokenCreateOffer;
import org.xrpl.xrpl4j.model.transactions.NfTokenId;
import org.xrpl.xrpl4j.model.transactions.NfTokenMint;
import org.xrpl.xrpl4j.model.transactions.NfTokenUri;
import org.xrpl.xrpl4j.model.transactions.TransactionResultCodes;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;

import java.util.Optional;

/**
 * IT for NfToken operations.
 */
public class NfTokenIT extends AbstractIT {

  @Test
  void mint() throws JsonRpcClientErrorException, JsonProcessingException {
    KeyPair keyPair = createRandomAccountEd25519();

    AccountInfoResult accountInfoResult = this.scanForResult(
      () -> this.getValidatedAccountInfo(keyPair.publicKey().deriveAddress())
    );

    NfTokenUri uri = NfTokenUri.ofPlainText("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf4dfuylqabf3oclgtqy55fbzdi");

    //Nft mint transaction
    NfTokenMint nfTokenMint = NfTokenMint.builder()
      .tokenTaxon(UnsignedLong.ONE)
      .account(keyPair.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(keyPair.publicKey())
      .sequence(accountInfoResult.accountData().sequence())
      .uri(uri)
      .build();

    SingleSignedTransaction<NfTokenMint> signedMint = signatureService.sign(keyPair.privateKey(), nfTokenMint);
    SubmitResult<NfTokenMint> mintSubmitResult = xrplClient.submit(signedMint);
    assertThat(mintSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedMint.hash()).isEqualTo(mintSubmitResult.transactionResult().hash());

    NfTokenObject nfToken = this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(keyPair.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    ).accountNfts()
      .stream().filter(nft -> nft.uri().get().equals(uri))
      .findFirst()
      .get();

    Optional<NfTokenPageObject> maybeNfTokenPage = xrplClient.accountObjects(
        AccountObjectsRequestParams.of(nfTokenMint.account())
      ).accountObjects().stream()
      .filter(object -> NfTokenPageObject.class.isAssignableFrom(object.getClass()))
      .map(object -> (NfTokenPageObject) object)
      .findFirst();

    assertThat(maybeNfTokenPage).isNotEmpty();
    assertThat(maybeNfTokenPage.get().nfTokens()).contains(
      NfTokenWrapper.of(
        NfToken.builder()
          .nfTokenId(nfToken.nfTokenId())
          .uri(nfToken.uri())
          .build()
      )
    );

    AccountInfoResult minterAccountInfo = xrplClient.accountInfo(
      AccountInfoRequestParams.of(keyPair.publicKey().deriveAddress())
    );
    assertThat(minterAccountInfo.accountData().mintedNfTokens()).isNotEmpty().get().isEqualTo(UnsignedInteger.ONE);
    assertThat(minterAccountInfo.accountData().burnedNfTokens()).isEmpty();
    assertThat(minterAccountInfo.accountData().nfTokenMinter()).isEmpty();
    logger.info("NFT was minted successfully.");
  }

  @Test
  void mintFromOtherMinterAccount() throws JsonRpcClientErrorException, JsonProcessingException {
    KeyPair keyPair = createRandomAccountEd25519();
    KeyPair minterKeyPair = createRandomAccountEd25519();

    AccountInfoResult accountInfoResult = this.scanForResult(
      () -> this.getValidatedAccountInfo(keyPair.publicKey().deriveAddress())
    );


    AccountSet accountSet = AccountSet.builder()
      .account(keyPair.publicKey().deriveAddress())
      .sequence(accountInfoResult.accountData().sequence())
      .fee(FeeUtils.computeNetworkFees(xrplClient.fee()).recommendedFee())
      .mintAccount(minterKeyPair.publicKey().deriveAddress())
      .setFlag(AccountSet.AccountSetFlag.AUTHORIZED_MINTER)
      .signingPublicKey(keyPair.publicKey())
      .build();

    SingleSignedTransaction<AccountSet> signedAccountSet = signatureService.sign(keyPair.privateKey(), accountSet);
    SubmitResult<AccountSet> accountSetSubmitResult = xrplClient.submit(signedAccountSet);
    assertThat(accountSetSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedAccountSet.hash()).isEqualTo(accountSetSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> this.getValidatedAccountInfo(keyPair.publicKey().deriveAddress()),
      result -> result.accountData().nfTokenMinter().isPresent() &&
        result.accountData().nfTokenMinter().get().equals(minterKeyPair.publicKey().deriveAddress())
    );

    NfTokenUri uri = NfTokenUri.ofPlainText("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf4dfuylqabf3oclgtqy55fbzdi");

    AccountInfoResult minterAccountInfo = this.scanForResult(
      () -> this.getValidatedAccountInfo(minterKeyPair.publicKey().deriveAddress())
    );

    //Nft mint transaction
    NfTokenMint nfTokenMint = NfTokenMint.builder()
      .tokenTaxon(UnsignedLong.ONE)
      .account(minterKeyPair.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(minterKeyPair.publicKey())
      .sequence(minterAccountInfo.accountData().sequence())
      .issuer(keyPair.publicKey().deriveAddress())
      .uri(uri)
      .build();

    SingleSignedTransaction<NfTokenMint> signedMint = signatureService.sign(minterKeyPair.privateKey(), nfTokenMint);
    SubmitResult<NfTokenMint> mintSubmitResult = xrplClient.submit(signedMint);
    assertThat(mintSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedMint.hash()).isEqualTo(mintSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(minterKeyPair.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          logger.error("Exception occurred while getting account nfts: {}", e.getMessage(), e);
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    );

    AccountInfoResult sourceAccountInfoAfterMint = xrplClient.accountInfo(
      AccountInfoRequestParams.of(keyPair.publicKey().deriveAddress())
    );
    assertThat(sourceAccountInfoAfterMint.accountData().mintedNfTokens()).isNotEmpty().get()
      .isEqualTo(UnsignedInteger.ONE);
    assertThat(sourceAccountInfoAfterMint.accountData().burnedNfTokens()).isEmpty();
    assertThat(sourceAccountInfoAfterMint.accountData().nfTokenMinter()).isNotEmpty().get()
      .isEqualTo(minterKeyPair.publicKey().deriveAddress());
    logger.info("NFT was minted successfully.");
  }

  @Test
  void mintAndBurn() throws JsonRpcClientErrorException, JsonProcessingException {
    KeyPair keyPair = createRandomAccountEd25519();

    AccountInfoResult accountInfoResult = this.scanForResult(
      () -> this.getValidatedAccountInfo(keyPair.publicKey().deriveAddress())
    );

    // nft mint
    NfTokenUri uri = NfTokenUri.ofPlainText("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf4dfuylqabf3oclgtqy55fbzdi");

    NfTokenMint nfTokenMint = NfTokenMint.builder()
      .tokenTaxon(UnsignedLong.ONE)
      .account(keyPair.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(keyPair.publicKey())
      .sequence(accountInfoResult.accountData().sequence())
      .uri(uri)
      .build();

    SingleSignedTransaction<NfTokenMint> signedMint = signatureService.sign(keyPair.privateKey(), nfTokenMint);
    SubmitResult<NfTokenMint> mintSubmitResult = xrplClient.submit(signedMint);
    assertThat(mintSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedMint.hash()).isEqualTo(mintSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(keyPair.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    );
    logger.info("NFT was minted successfully.");

    // nft burn
    AccountNftsResult accountNftsResult = xrplClient.accountNfts(keyPair.publicKey().deriveAddress());

    NfTokenId tokenId = accountNftsResult.accountNfts().get(0).nfTokenId();

    NfTokenBurn nfTokenBurn = NfTokenBurn.builder()
      .nfTokenId(tokenId)
      .account(keyPair.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(keyPair.publicKey())
      .sequence(accountInfoResult.accountData().sequence().plus(UnsignedInteger.ONE))
      .build();

    SingleSignedTransaction<NfTokenBurn> signedBurn = signatureService.sign(keyPair.privateKey(), nfTokenBurn);
    SubmitResult<NfTokenBurn> burnSubmitResult = xrplClient.submit(signedBurn);
    assertThat(burnSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedBurn.hash()).isEqualTo(burnSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(keyPair.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .noneMatch(nft -> nft.uri().get().equals(uri))
    );

    AccountNftsResult accountNftsResult1 = xrplClient.accountNfts(keyPair.publicKey().deriveAddress());
    assertThat(
      accountNftsResult1.accountNfts().stream().noneMatch(
        object -> object.equals(NfTokenObject.builder().nfTokenId(tokenId).build())
      )
    ).isTrue();

    AccountInfoResult minterAccountInfo = xrplClient.accountInfo(
      AccountInfoRequestParams.of(keyPair.publicKey().deriveAddress())
    );
    assertThat(minterAccountInfo.accountData().mintedNfTokens()).isNotEmpty().get().isEqualTo(UnsignedInteger.ONE);
    assertThat(minterAccountInfo.accountData().burnedNfTokens()).isNotEmpty().get().isEqualTo(UnsignedInteger.ONE);
    assertThat(minterAccountInfo.accountData().nfTokenMinter()).isEmpty();
    logger.info("NFT burned successfully.");
  }

  @Test
  void mintAndCreateOffer() throws JsonRpcClientErrorException, JsonProcessingException {
    KeyPair keyPair = createRandomAccountEd25519();

    //mint NFT from one account
    AccountInfoResult accountInfoResult = this.scanForResult(
      () -> this.getValidatedAccountInfo(keyPair.publicKey().deriveAddress())
    );
    NfTokenUri uri = NfTokenUri.ofPlainText("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf4dfuylqabf3oclgtqy55fbzdi");

    //Nft mint transaction
    NfTokenMint nfTokenMint = NfTokenMint.builder()
      .tokenTaxon(UnsignedLong.ONE)
      .account(keyPair.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(keyPair.publicKey())
      .sequence(accountInfoResult.accountData().sequence())
      .uri(uri)
      .build();

    SingleSignedTransaction<NfTokenMint> signedMint = signatureService.sign(keyPair.privateKey(), nfTokenMint);
    SubmitResult<NfTokenMint> mintSubmitResult = xrplClient.submit(signedMint);
    assertThat(mintSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedMint.hash()).isEqualTo(mintSubmitResult.transactionResult().hash());
    logger.info(
      "NFTokenMint transaction successful: https://testnet.xrpl.org/transactions/{}",
      mintSubmitResult.transactionResult().hash()
    );

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(keyPair.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    );
    logger.info("NFT was minted successfully.");

    //create a sell offer for the NFT that was created above
    NfTokenId tokenId = xrplClient.accountNfts(keyPair.publicKey().deriveAddress()).accountNfts().get(0).nfTokenId();

    NfTokenCreateOffer nfTokenCreateOffer = NfTokenCreateOffer.builder()
      .account(keyPair.publicKey().deriveAddress())
      .nfTokenId(tokenId)
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult.accountData().sequence().plus(UnsignedInteger.ONE))
      .amount(XrpCurrencyAmount.ofDrops(1000))
      .flags(NfTokenCreateOfferFlags.builder()
        .tfSellToken(true)
        .build())
      .signingPublicKey(keyPair.publicKey())
      .build();

    SingleSignedTransaction<NfTokenCreateOffer> signedOffer = signatureService.sign(
      keyPair.privateKey(),
      nfTokenCreateOffer
    );
    SubmitResult<NfTokenCreateOffer> nfTokenCreateOfferSubmitResult = xrplClient.submit(signedOffer);
    assertThat(nfTokenCreateOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedOffer.hash()).isEqualTo(nfTokenCreateOfferSubmitResult.transactionResult().hash());

    //verify the offer was created
    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenCreateOfferSubmitResult.transactionResult().hash(),
        NfTokenCreateOffer.class
      )
    );
    logger.info("NFT Create Offer (Sell) transaction was validated successfully.");

    this.scanForResult(
      () -> this.getValidatedAccountObjects(keyPair.publicKey().deriveAddress()),
      objectsResult -> objectsResult.accountObjects().stream()
        .anyMatch(object ->
          NfTokenOfferObject.class.isAssignableFrom(object.getClass()) &&
            ((NfTokenOfferObject) object).owner().equals(keyPair.publicKey().deriveAddress())
        )
    );
    logger.info("NFTokenOffer object was found in account's objects.");
  }

  @Test
  void mintAndCreateThenAcceptOffer() throws JsonRpcClientErrorException, JsonProcessingException {
    KeyPair wallet = createRandomAccountEd25519();

    //mint NFT from one account
    AccountInfoResult accountInfoResult = this.scanForResult(
      () -> this.getValidatedAccountInfo(wallet.publicKey().deriveAddress())
    );
    NfTokenUri uri = NfTokenUri.ofPlainText("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf4dfuylqabf3oclgtqy55fbzdi");

    //Nft mint transaction
    NfTokenMint nfTokenMint = NfTokenMint.builder()
      .tokenTaxon(UnsignedLong.ONE)
      .account(wallet.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(wallet.publicKey())
      .sequence(accountInfoResult.accountData().sequence())
      .flags(NfTokenMintFlags.builder()
        .tfTransferable(true)
        .build())
      .uri(uri)
      .build();

    SingleSignedTransaction<NfTokenMint> signedMint = signatureService.sign(wallet.privateKey(), nfTokenMint);
    SubmitResult<NfTokenMint> mintSubmitResult = xrplClient.submit(signedMint);
    assertThat(mintSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedMint.hash()).isEqualTo(mintSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(wallet.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    );
    logger.info("NFT was minted successfully.");

    //create a sell offer for the NFT that was created above

    NfTokenId tokenId = xrplClient.accountNfts(wallet.publicKey().deriveAddress()).accountNfts().get(0).nfTokenId();

    // create buy offer from another account
    KeyPair wallet2 = createRandomAccountEd25519();

    AccountInfoResult accountInfoResult2 = this.scanForResult(
      () -> this.getValidatedAccountInfo(wallet2.publicKey().deriveAddress())
    );

    NfTokenCreateOffer nfTokenCreateOffer = NfTokenCreateOffer.builder()
      .account(wallet2.publicKey().deriveAddress())
      .owner(wallet.publicKey().deriveAddress())
      .nfTokenId(tokenId)
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult2.accountData().sequence())
      .amount(XrpCurrencyAmount.ofDrops(1000))
      .signingPublicKey(wallet2.publicKey())
      .build();

    SingleSignedTransaction<NfTokenCreateOffer> signedOffer = signatureService.sign(
      wallet2.privateKey(),
      nfTokenCreateOffer
    );
    SubmitResult<NfTokenCreateOffer> nfTokenCreateOfferSubmitResult = xrplClient.submit(signedOffer);
    assertThat(nfTokenCreateOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedOffer.hash()).isEqualTo(nfTokenCreateOfferSubmitResult.transactionResult().hash());

    //verify the offer was created
    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenCreateOfferSubmitResult.transactionResult().hash(),
        NfTokenCreateOffer.class
      )
    );
    logger.info("NFT Create Offer (Buy) transaction was validated successfully.");

    this.scanForResult(
      () -> this.getValidatedAccountObjects(wallet2.publicKey().deriveAddress()),
      objectsResult -> objectsResult.accountObjects().stream()
        .anyMatch(object ->
          NfTokenOfferObject.class.isAssignableFrom(object.getClass()) &&
            ((NfTokenOfferObject) object).owner().equals(wallet2.publicKey().deriveAddress())
        )
    );
    logger.info("NFTokenOffer object was found in account's objects.");

    NftBuyOffersResult nftBuyOffersResult = xrplClient.nftBuyOffers(NftBuyOffersRequestParams.builder()
      .nfTokenId(tokenId)
      .build());
    assertThat(nftBuyOffersResult.offers().size()).isEqualTo(1);

    // accept offer from a different account
    NfTokenAcceptOffer nfTokenAcceptOffer = NfTokenAcceptOffer.builder()
      .account(wallet.publicKey().deriveAddress())
      .buyOffer(nftBuyOffersResult.offers().get(0).nftOfferIndex())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult.accountData().sequence().plus(UnsignedInteger.ONE))
      .signingPublicKey(wallet.publicKey())
      .build();

    SingleSignedTransaction<NfTokenAcceptOffer> signedAccept = signatureService.sign(
      wallet.privateKey(),
      nfTokenAcceptOffer
    );
    SubmitResult<NfTokenAcceptOffer> nfTokenAcceptOfferSubmitResult = xrplClient.submit(signedAccept);
    assertThat(nfTokenAcceptOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedAccept.hash()).isEqualTo(nfTokenAcceptOfferSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenAcceptOfferSubmitResult.transactionResult().hash(),
        NfTokenAcceptOffer.class
      )
    );
    logger.info("NFT Accept Offer transaction was validated successfully.");

    assertThat(xrplClient.accountNfts(wallet.publicKey().deriveAddress()).accountNfts().size()).isEqualTo(0);
    assertThat(xrplClient.accountNfts(wallet2.publicKey().deriveAddress()).accountNfts().size()).isEqualTo(1);

    logger.info("The NFT ownership was transferred.");
  }

  @Test
  void mintAndCreateOfferThenCancelOffer() throws JsonRpcClientErrorException, JsonProcessingException {
    KeyPair wallet = createRandomAccountEd25519();

    //mint NFT from one account
    AccountInfoResult accountInfoResult = this.scanForResult(
      () -> this.getValidatedAccountInfo(wallet.publicKey().deriveAddress())
    );
    NfTokenUri uri = NfTokenUri.ofPlainText("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf4dfuylqabf3oclgtqy55fbzdi");

    //Nft mint transaction
    NfTokenMint nfTokenMint = NfTokenMint.builder()
      .tokenTaxon(UnsignedLong.ONE)
      .account(wallet.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(wallet.publicKey())
      .sequence(accountInfoResult.accountData().sequence())
      .uri(uri)
      .build();

    SingleSignedTransaction<NfTokenMint> signedMint = signatureService.sign(wallet.privateKey(), nfTokenMint);
    SubmitResult<NfTokenMint> mintSubmitResult = xrplClient.submit(signedMint);
    assertThat(mintSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedMint.hash()).isEqualTo(mintSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(wallet.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    );
    logger.info("NFT was minted successfully.");

    //create a sell offer for the NFT that was created above

    NfTokenId tokenId = xrplClient.accountNfts(wallet.publicKey().deriveAddress()).accountNfts().get(0).nfTokenId();

    NfTokenCreateOffer nfTokenCreateOffer = NfTokenCreateOffer.builder()
      .account(wallet.publicKey().deriveAddress())
      .nfTokenId(tokenId)
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult.accountData().sequence().plus(UnsignedInteger.ONE))
      .amount(XrpCurrencyAmount.ofDrops(1000))
      .flags(NfTokenCreateOfferFlags.SELL_NFTOKEN)
      .signingPublicKey(wallet.publicKey())
      .build();

    SingleSignedTransaction<NfTokenCreateOffer> signedOffer = signatureService.sign(
      wallet.privateKey(),
      nfTokenCreateOffer
    );
    SubmitResult<NfTokenCreateOffer> nfTokenCreateOfferSubmitResult = xrplClient.submit(signedOffer);
    assertThat(nfTokenCreateOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedOffer.hash()).isEqualTo(nfTokenCreateOfferSubmitResult.transactionResult().hash());

    //verify the offer was created
    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenCreateOfferSubmitResult.transactionResult().hash(),
        NfTokenCreateOffer.class
      )
    );
    logger.info("NFT Create Offer (Sell) transaction was validated successfully.");

    this.scanForResult(
      () -> this.getValidatedAccountObjects(wallet.publicKey().deriveAddress()),
      objectsResult -> objectsResult.accountObjects().stream()
        .anyMatch(object ->
          NfTokenOfferObject.class.isAssignableFrom(object.getClass()) &&
            ((NfTokenOfferObject) object).owner().equals(wallet.publicKey().deriveAddress())
        )
    );
    logger.info("NFTokenOffer object was found in account's objects.");

    NftSellOffersResult nftSellOffersResult = xrplClient.nftSellOffers(NftSellOffersRequestParams.builder()
      .nfTokenId(tokenId)
      .build());

    // cancel the created offer
    NfTokenCancelOffer nfTokenCancelOffer = NfTokenCancelOffer.builder()
      .addTokenOffers(nftSellOffersResult.offers().get(0).nftOfferIndex())
      .account(wallet.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult.accountData().sequence().plus(UnsignedInteger.valueOf(2)))
      .signingPublicKey(wallet.publicKey())
      .build();

    SingleSignedTransaction<NfTokenCancelOffer> signedCancel = signatureService.sign(
      wallet.privateKey(),
      nfTokenCancelOffer
    );
    SubmitResult<NfTokenCancelOffer> nfTokenCancelOfferSubmitResult = xrplClient.submit(signedCancel);
    assertThat(nfTokenCancelOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedCancel.hash()).isEqualTo(nfTokenCancelOfferSubmitResult.transactionResult().hash());

    //verify the offer was created
    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenCancelOfferSubmitResult.transactionResult().hash(),
        NfTokenCreateOffer.class
      )
    );
    logger.info("NFT Cancel Offer transaction was validated successfully.");

    this.scanForResult(
      () -> this.getValidatedAccountObjects(wallet.publicKey().deriveAddress()),
      objectsResult -> objectsResult.accountObjects().stream()
        .noneMatch(object ->
          NfTokenOfferObject.class.isAssignableFrom(object.getClass())
        )
    );
    logger.info("NFTokenOffer object was deleted successfully.");
  }

  @Test
  void acceptOfferDirectModeWithBrokerFee() throws JsonRpcClientErrorException, JsonProcessingException {
    KeyPair wallet = createRandomAccountEd25519();

    //mint NFT from one account
    AccountInfoResult accountInfoResult = this.scanForResult(
      () -> this.getValidatedAccountInfo(wallet.publicKey().deriveAddress())
    );
    NfTokenUri uri = NfTokenUri.ofPlainText("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf4dfuylqabf3oclgtqy55fbzdi");

    //Nft mint transaction
    NfTokenMint nfTokenMint = NfTokenMint.builder()
      .tokenTaxon(UnsignedLong.ONE)
      .account(wallet.publicKey().deriveAddress())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .signingPublicKey(wallet.publicKey())
      .sequence(accountInfoResult.accountData().sequence())
      .flags(NfTokenMintFlags.builder()
        .tfTransferable(true)
        .build())
      .uri(uri)
      .build();

    SingleSignedTransaction<NfTokenMint> signedMint = signatureService.sign(wallet.privateKey(), nfTokenMint);
    SubmitResult<NfTokenMint> mintSubmitResult = xrplClient.submit(signedMint);
    assertThat(mintSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedMint.hash()).isEqualTo(mintSubmitResult.transactionResult().hash());

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(wallet.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    );
    logger.info("NFT was minted successfully.");

    //create a sell offer for the NFT that was created above

    NfTokenId tokenId = xrplClient.accountNfts(wallet.publicKey().deriveAddress()).accountNfts().get(0).nfTokenId();

    // the owner creates the sell offer
    NfTokenCreateOffer nfTokenCreateSellOffer = NfTokenCreateOffer.builder()
      .account(wallet.publicKey().deriveAddress())
      .nfTokenId(tokenId)
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult.accountData().sequence().plus(UnsignedInteger.ONE))
      .amount(XrpCurrencyAmount.ofDrops(1000))
      .signingPublicKey(wallet.publicKey())
      .flags(NfTokenCreateOfferFlags.SELL_NFTOKEN)
      .build();

    SingleSignedTransaction<NfTokenCreateOffer> signedOffer = signatureService.sign(
      wallet.privateKey(),
      nfTokenCreateSellOffer
    );
    SubmitResult<NfTokenCreateOffer> nfTokenCreateSellOfferSubmitResult = xrplClient.submit(signedOffer);
    assertThat(nfTokenCreateSellOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedOffer.hash()).isEqualTo(nfTokenCreateSellOfferSubmitResult.transactionResult().hash());

    //verify the offer was created
    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenCreateSellOfferSubmitResult.transactionResult().hash(),
        NfTokenCreateOffer.class
      )
    );
    logger.info("NFT Create Offer (Sell) transaction was validated successfully.");

    this.scanForResult(
      () -> this.getValidatedAccountObjects(wallet.publicKey().deriveAddress()),
      objectsResult -> objectsResult.accountObjects().stream()
        .anyMatch(object ->
          NfTokenOfferObject.class.isAssignableFrom(object.getClass()) &&
            ((NfTokenOfferObject) object).owner().equals(wallet.publicKey().deriveAddress())
        )
    );
    logger.info("NFTokenOffer object was found in account's objects.");

    NftSellOffersResult nftSellOffersResult = xrplClient.nftSellOffers(NftSellOffersRequestParams.builder()
      .nfTokenId(tokenId)
      .build());
    assertThat(nftSellOffersResult.offers().size()).isEqualTo(1);

    // create buy offer from another account
    KeyPair wallet2 = createRandomAccountEd25519();

    AccountInfoResult accountInfoResult2 = this.scanForResult(
      () -> this.getValidatedAccountInfo(wallet2.publicKey().deriveAddress())
    );

    NfTokenCreateOffer nfTokenCreateOffer = NfTokenCreateOffer.builder()
      .account(wallet2.publicKey().deriveAddress())
      .owner(wallet.publicKey().deriveAddress())
      .nfTokenId(tokenId)
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult2.accountData().sequence())
      .amount(XrpCurrencyAmount.ofDrops(1000))
      .signingPublicKey(wallet2.publicKey())
      .build();

    SingleSignedTransaction<NfTokenCreateOffer> signedOffer2 = signatureService.sign(
      wallet2.privateKey(),
      nfTokenCreateOffer
    );
    SubmitResult<NfTokenCreateOffer> nfTokenCreateOfferSubmitResult = xrplClient.submit(signedOffer2);
    assertThat(nfTokenCreateOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedOffer2.hash()).isEqualTo(nfTokenCreateOfferSubmitResult.transactionResult().hash());

    //verify the offer was created
    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenCreateOfferSubmitResult.transactionResult().hash(),
        NfTokenCreateOffer.class
      )
    );
    logger.info("NFT Create Offer (Buy) transaction was validated successfully.");

    this.scanForResult(
      () -> this.getValidatedAccountObjects(wallet2.publicKey().deriveAddress()),
      objectsResult -> objectsResult.accountObjects().stream()
        .anyMatch(object ->
          NfTokenOfferObject.class.isAssignableFrom(object.getClass()) &&
            ((NfTokenOfferObject) object).owner().equals(wallet2.publicKey().deriveAddress())
        )
    );
    logger.info("NFTokenOffer object was found in account's objects.");

    NftBuyOffersResult nftBuyOffersResult = xrplClient.nftBuyOffers(NftBuyOffersRequestParams.builder()
      .nfTokenId(tokenId)
      .build());
    assertThat(nftBuyOffersResult.offers().size()).isEqualTo(1);

    // accept offer from a different account
    KeyPair wallet3 = createRandomAccountEd25519();

    AccountInfoResult accountInfoResult3 = this.scanForResult(
      () -> this.getValidatedAccountInfo(wallet3.publicKey().deriveAddress())
    );
    NfTokenAcceptOffer nfTokenAcceptOffer = NfTokenAcceptOffer.builder()
      .account(wallet3.publicKey().deriveAddress())
      .buyOffer(nftBuyOffersResult.offers().get(0).nftOfferIndex())
      .sellOffer(nftSellOffersResult.offers().get(0).nftOfferIndex())
      .fee(XrpCurrencyAmount.ofDrops(50))
      .sequence(accountInfoResult3.accountData().sequence())
      .signingPublicKey(wallet3.publicKey())
      .build();

    SingleSignedTransaction<NfTokenAcceptOffer> signedAccept = signatureService.sign(
      wallet3.privateKey(),
      nfTokenAcceptOffer
    );
    SubmitResult<NfTokenAcceptOffer> nfTokenAcceptOfferSubmitResult = xrplClient.submit(signedAccept);
    assertThat(nfTokenAcceptOfferSubmitResult.engineResult()).isEqualTo(TransactionResultCodes.TES_SUCCESS);
    assertThat(signedAccept.hash()).isEqualTo(nfTokenAcceptOfferSubmitResult.transactionResult().hash());

    //verify the offer was accepted
    this.scanForResult(
      () -> this.getValidatedTransaction(
        nfTokenAcceptOfferSubmitResult.transactionResult().hash(),
        NfTokenAcceptOffer.class
      )
    );

    this.scanForResult(
      () -> {
        try {
          return xrplClient.accountNfts(wallet2.publicKey().deriveAddress());
        } catch (JsonRpcClientErrorException e) {
          throw new RuntimeException(e);
        }
      },
      result -> result.accountNfts().stream()
        .anyMatch(nft -> nft.uri().get().equals(uri))
    );
    logger.info("NFT was transferred successfully.");
  }
}
