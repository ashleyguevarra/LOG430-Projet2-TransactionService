package projet2.banks.transaction.dto;

public record KeyResolvedResponse(
    Integer bankId,
    String maskedBeneficiaryName
) {}
