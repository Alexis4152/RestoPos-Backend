package com.nexora.restopos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrdenCreateRequest {
    // null = orden para llevar/sin mesa
    private Long mesaId;
    @Min(1)
    private Integer numeroComensales = 1;
    private String notas;
    // Opcional, solo tiene sentido para una orden para llevar (mesaId == null) — ver
    // Orden.direccion. max=200: debe coincidir con esa columna (VARCHAR(200)).
    @Size(max = 200, message = "La dirección no puede tener más de 200 caracteres")
    private String direccion;
    // Obligatorios cuando mesaId == null (para llevar) — se validan en OrdenService#abrir.
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String clienteNombre;
    @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
    private String clienteTelefono;
    // true = envío a domicilio: agrega a la orden el cargo configurado por el admin
    // (TiendaInfo.costoEnvioDomicilio). Solo aplica a para llevar. Obsoleto: usar tipoEntrega
    // (se respeta solo si tipoEntrega no viene, para clientes viejos).
    private Boolean domicilio;
    // Solo sin mesa. Qué datos del cliente se piden (ver OrdenService#abrir):
    //  PARA_LLEVAR — el cliente está en caja y se lo lleva: solo nombre;
    //  COMER_AQUI  — ordena en caja y se sienta donde quiera (sin elegir mesa): solo nombre;
    //  RECOGER     — pidió por teléfono y viene por él: nombre y teléfono;
    //  DOMICILIO   — se le lleva: nombre, teléfono y dirección (+ costo de envío).
    private String tipoEntrega;
}
