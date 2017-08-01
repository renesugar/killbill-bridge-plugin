package org.killbill.billing.plugin.bridge.api.resolver.remote;

import com.google.common.base.Preconditions;
import org.killbill.billing.client.KillBillClient;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.plugin.bridge.api.resolver.ResolvingType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class RemoteResolverRequest {

    private final static AtomicLong RemoteResolverRequestSeq = new AtomicLong();

    private final Long requestId;
    private final Set<Request> requests;

    public RemoteResolverRequest() {
        this.requestId = RemoteResolverRequestSeq.incrementAndGet();
        this.requests = new HashSet<>();
    }


    public enum DefaultAction {
        CREATE_IF_MISSING,
        THROW_IF_MISSING,
        IGNORE_IF_MISSING
    }

    public RemoteResolverRequest resolveAccount(final String accountExternalKey, final DefaultAction defaultAction)  {
        if (accountExternalKey != null) {
            requests.add(new Request(ResolvingType.ACCOUNT, accountExternalKey, (client, requestOptions, response) -> {
                Account account = client.getAccount( accountExternalKey, requestOptions);
                if (defaultAction == DefaultAction.CREATE_IF_MISSING && account == null) {
                    final Account input = new Account();
                    input.setExternalKey(accountExternalKey);
                    account = client.createAccount(input, requestOptions);
                }
                response.setAccountIdMapping(account.getAccountId());
            }));
        }
        return this;
    }

    public RemoteResolverRequest resolvePM(final String pmExternalKey, final DefaultAction defaultAction) {
        if (pmExternalKey != null) {
            requests.add(new Request(ResolvingType.PAYMENT_METHOD, pmExternalKey, (client, requestOptions, response) -> {
                final PaymentMethod pm = client.getPaymentMethodByKey(pmExternalKey, requestOptions);
                if (defaultAction == DefaultAction.CREATE_IF_MISSING && pm == null) {
                    // TODO
                }
                response.setPaymentMethodIdMapping(pm.getPaymentMethodId());
            }));
        }
        return this;
    }

    public RemoteResolverRequest resolvePayment(final String paymentExternalKey, final DefaultAction defaultAction) {

        Preconditions.checkState(defaultAction == DefaultAction.IGNORE_IF_MISSING);
        if (paymentExternalKey != null) {
            requests.add(new Request(ResolvingType.PAYMENT, paymentExternalKey, (client, requestOptions, response) -> {
                final Payment payment = client.getPaymentByExternalKey(paymentExternalKey, requestOptions);
                if (payment != null) {
                    response.setPaymentIdMapping(payment.getPaymentId());
                }
            }));
        }
        return this;
    }

    public RemoteResolverRequest resolvePaymentTransaction(final String paymentExternalKey, final String transactionExternalKey, final DefaultAction defaultAction) {

        Preconditions.checkState(defaultAction == DefaultAction.IGNORE_IF_MISSING);
        if (paymentExternalKey != null) {
            if (transactionExternalKey != null) {
                requests.add(new Request(ResolvingType.PAYMENT_AND_TRANSACTION, paymentExternalKey, (client, requestOptions, response) -> {
                    final Payment payment = client.getPaymentByExternalKey(paymentExternalKey, requestOptions);
                    if (payment !=  null) {
                        response.setPaymentIdMapping(payment.getPaymentId());
                        final Optional<PaymentTransaction> transaction = payment.getTransactions().stream()
                                .filter(t -> t.getTransactionExternalKey().equals(transactionExternalKey))
                                .findAny();
                        if (transaction.isPresent()) {
                            response.setTransactionIdMapping(transaction.get().getTransactionId());
                        }
                    }
                }));
            } else {
                resolvePayment(paymentExternalKey, defaultAction);
            }
        }
        return this;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Set<Request> getRequests() {
        return requests;
    }

    public interface Resolver {
        void resolveIds(final KillBillClient client, final RequestOptions requestOptions, final RemoteResolverResponse.RemoteResolverResponseBuilder response) throws KillBillClientException;
    }

    public static class Request {

        private final ResolvingType type;
        private final String srcKey;
        private final Resolver resolver;


        public Request(final ResolvingType type, final String srcKey, final Resolver resolver) {
            this.type = type;
            this.srcKey = srcKey;
            this.resolver = resolver;
        }

        public void resolve(final KillBillClient client, final RequestOptions requestOptions, final RemoteResolverResponse.RemoteResolverResponseBuilder response) throws KillBillClientException {
            resolver.resolveIds(client, requestOptions, response);
        }

        public ResolvingType getType() {
            return type;
        }

        public String getSrcKey() {
            return srcKey;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Request)) return false;

            final Request request = (Request) o;

            if (type != request.type) return false;
            return srcKey != null ? srcKey.equals(request.srcKey) : request.srcKey == null;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (srcKey != null ? srcKey.hashCode() : 0);
            return result;
        }
    }
}
