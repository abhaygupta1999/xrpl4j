package com.ripple.xrpl4j.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import com.ripple.xrpl4j.model.transactions.Address;
import com.ripple.xrpl4j.model.transactions.Flags;
import com.ripple.xrpl4j.model.transactions.Transaction;
import com.ripple.xrpl4j.wallet.DefaultWalletFactory;
import com.ripple.xrpl4j.wallet.SeedWalletGenerationResult;
import com.ripple.xrpl4j.wallet.Wallet;
import com.ripple.xrpl4j.wallet.WalletFactory;
import com.ripple.xrplj4.client.XrplClient;
import com.ripple.xrplj4.client.faucet.FaucetAccountResponse;
import com.ripple.xrplj4.client.faucet.FaucetClient;
import com.ripple.xrplj4.client.faucet.FundAccountRequest;
import com.ripple.xrplj4.client.model.JsonRpcResult;
import com.ripple.xrplj4.client.model.accounts.AccountInfoRequestParams;
import com.ripple.xrplj4.client.model.accounts.AccountInfoResult;
import com.ripple.xrplj4.client.model.accounts.AccountObjectsRequestParams;
import com.ripple.xrplj4.client.model.accounts.AccountObjectsResult;
import com.ripple.xrplj4.client.model.accounts.ImmutableAccountInfoRequestParams;
import com.ripple.xrplj4.client.model.accounts.ImmutableAccountObjectsRequestParams;
import com.ripple.xrplj4.client.model.transactions.TransactionRequestParams;
import com.ripple.xrplj4.client.model.transactions.TransactionResult;
import com.ripple.xrplj4.client.rippled.JsonRpcClientErrorException;
import okhttp3.HttpUrl;
import org.awaitility.Duration;
import org.awaitility.core.ConditionTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbstractIT {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  protected final FaucetClient faucetClient =
    FaucetClient.construct(HttpUrl.parse("https://faucet.altnet.rippletest.net"));

  protected final XrplClient xrplClient = new XrplClient(HttpUrl.parse("https://s.altnet.rippletest.net:51234"));
  protected final WalletFactory walletFactory = DefaultWalletFactory.getInstance();

  protected Wallet createRandomAccount() {
    ///////////////////////
    // Create the account
    SeedWalletGenerationResult seedResult = walletFactory.randomWallet(true);
    final Wallet wallet = seedResult.wallet();
    logger.info("Generated testnet wallet with address {}", wallet.xAddress());

    ///////////////////////
    // Fund the account
    FaucetAccountResponse fundResponse = faucetClient.fundAccount(FundAccountRequest.of(wallet.classicAddress().value()));
    logger.info("Account has been funded: {}", fundResponse);
    assertThat(fundResponse.amount()).isGreaterThan(0);
    return wallet;
  }

  //////////////////////
  // Ledger Helpers
  //////////////////////

  protected <T extends JsonRpcResult> T scanForResult(Supplier<T> resultSupplier, Predicate<T> condition) {
    return given()
      .atMost(Duration.ONE_MINUTE.divide(2))
      .await()
      .until(() -> {
        T result = resultSupplier.get();
        if (result == null) {
          return null;
        }
        return condition.test(result) ? result : null;
      }, is(notNullValue()));
  }

  protected <T extends JsonRpcResult> T scanForResult(Supplier<T> resultSupplier) {
    Objects.requireNonNull(resultSupplier);
    return given()
      .atMost(Duration.ONE_MINUTE.divide(2))
      .ignoreException(RuntimeException.class)
      .await()
      .until(resultSupplier::get, is(notNullValue()));
  }

  protected AccountObjectsResult getValidatedAccountObjects(Address classicAddress) {
    try {
      AccountObjectsRequestParams params = AccountObjectsRequestParams.builder()
        .account(classicAddress)
        .ledgerIndex("validated")
        .build();
      return xrplClient.accountObjects(params);
    } catch (JsonRpcClientErrorException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  protected AccountInfoResult getValidatedAccountInfo(Address classicAddress) {
    try {
      AccountInfoRequestParams params = AccountInfoRequestParams.builder()
        .account(classicAddress)
        .ledgerIndex("validated")
        .build();
      return xrplClient.accountInfo(params);
    } catch (Exception | JsonRpcClientErrorException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  protected  <TxnType extends Transaction<? extends Flags>> TransactionResult<TxnType> getValidatedTransaction(
    String transactionHash,
    Class<TxnType> transactionType
  ) {
    try {
      TransactionResult<TxnType> transaction = xrplClient.transaction(
        TransactionRequestParams.of(transactionHash),
        transactionType
      );
      return transaction.validated() ? transaction : null;
    } catch (JsonRpcClientErrorException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

}
