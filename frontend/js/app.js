import { api } from "./api.js";

const grid = document.getElementById("product-grid");
const form = document.getElementById("add-product-form");
const submitButton = form.querySelector("button[type='submit']");
const productSearchForm = document.getElementById("search-product-form");
const productEditForm = document.getElementById("update-product-form");

const clientesGrid = document.getElementById("clientes-grid");
const clientesForm = document.getElementById("add-client-form");
const clientesSearchForm = document.getElementById("search-client-form");
const clientesEditForm = document.getElementById("update-client-form");
const cuentasGrid = document.getElementById("cuentas-grid");
const cuentasForm = document.getElementById("add-account-form");
const transferForm = document.getElementById("transfer-form");
const cuentaSearchForm = document.getElementById("account-search-form");
const cuentaSearchIdForm = document.getElementById("search-account-by-id-form");
const cuentaEditForm = document.getElementById("update-account-form");
const ventasForm = document.getElementById("create-venta-form");
const ventasGrid = document.getElementById("ventas-grid");
const ventasSearchForm = document.getElementById("search-venta-form");
const ventaActionForm = document.getElementById("venta-action-form");
const auditoriaGrid = document.getElementById("auditoria-grid");
const auditoriaForm = document.getElementById("auditoria-form");
const auditoriaFilterForm = document.getElementById("audit-filter-form");
const auditoriaProductFilterForm = document.getElementById(
  "audit-product-filter-form",
);
const stockForm = document.getElementById("stock-form");
const stockGrid = document.getElementById("stock-grid");
const recomendacionesForm = document.getElementById("recomendaciones-form");
const recomendacionesGrid = document.getElementById("recomendaciones-grid");

function currency(value) {
  return new Intl.NumberFormat("es-AR", {
    style: "currency",
    currency: "ARS",
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);
}

function setGridMessage(container, message, variant = "neutral") {
  container.innerHTML = `<p class="grid-message ${variant}">${message}</p>`;
}

function renderLoadingState(container) {
  setGridMessage(container, "Sincronizando con base de datos...", "neutral");
}

function cardTemplate(product) {
  return `
    <article class="card">
      <div class="card-topline">
        <span class="badge ${String(product.estadoStock).toLowerCase()}">${product.estadoStock}</span>
        <span class="id-pill">#${product.id}</span>
      </div>
      <h3>${product.nombre}</h3>
      <p class="price">${currency(product.precio)}</p>
      <div class="meta">
        <div><span>Stock</span><strong>${product.stockFisico}</strong></div>
        <div><span>Disponible</span><strong>${product.stockDisponible ?? product.stockFisico}</strong></div>
        <div><span>Categoría</span><strong>${product.categoria}</strong></div>
      </div>
      <button type="button" class="btn-delete" data-id="${product.id}" aria-label="Eliminar ${product.nombre}">Eliminar</button>
    </article>
  `;
}

function renderProductos(productos) {
  if (!productos.length) {
    setGridMessage(grid, "Sin datos o error de conexión.", "danger");
    return;
  }

  grid.innerHTML = productos.map(cardTemplate).join("");
}

function renderClientes(clientes) {
  if (!clientes.length) {
    setGridMessage(clientesGrid, "No hay clientes registrados.", "neutral");
    return;
  }

  clientesGrid.innerHTML = clientes
    .map(
      (c) => `
        <article class="card">
          <div class="card-topline"><span class="id-pill">#${c.id}</span><span class="badge active">${c.activo ? "Activo" : "Inactivo"}</span></div>
          <h3>${c.nombreCompleto || `${c.nombre} ${c.apellido}`}</h3>
          <div class="meta">
            <div><span>DNI</span><strong>${c.dni}</strong></div>
            <div><span>Localidad</span><strong>${c.localidad}</strong></div>
            <div><span>CUIT</span><strong>${c.cuit || "-"}</strong></div>
          </div>
          <div class="card-actions">
            <button type="button" class="btn-delete btn-client-delete" data-id="${c.id}" aria-label="Eliminar cliente ${c.id}">Eliminar</button>
            <button type="button" class="btn-neon btn-client-edit" data-id="${c.id}" aria-label="Editar cliente ${c.id}">Editar</button>
          </div>
        </article>
      `,
    )
    .join("");
}

function renderCuentas(cuentas) {
  if (!cuentas.length) {
    setGridMessage(cuentasGrid, "No hay cuentas registradas.", "neutral");
    return;
  }

  cuentasGrid.innerHTML = cuentas
    .map(
      (c) => `
        <article class="card">
          <div class="card-topline"><span class="id-pill">#${c.id}</span><span class="badge active">${c.rolUsuario}</span></div>
          <h3>${c.nombreCuenta}</h3>
          <div class="meta">
            <div><span>Correo</span><strong>${c.correoElectronico}</strong></div>
            <div><span>Saldo</span><strong>${currency(c.saldo)}</strong></div>
            <div><span>Cliente</span><strong>${c.idCliente}</strong></div>
          </div>
        </article>
      `,
    )
    .join("");
}

function renderAuditoria(items) {
  if (!items.length) {
    setGridMessage(auditoriaGrid, "Sin registros de auditoría.", "neutral");
    return;
  }

  auditoriaGrid.innerHTML = items
    .map(
      (a) => `
        <article class="card">
          <div class="card-topline"><span class="id-pill">#${a.id}</span><span class="badge neutral">${a.accion}</span></div>
          <h3>${a.descripcion}</h3>
          <div class="meta">
            <div><span>Entidad</span><strong>${a.idEntidad}</strong></div>
            <div><span>Fecha</span><strong>${a.fecha}</strong></div>
          </div>
        </article>
      `,
    )
    .join("");
}

function renderSimple(container, list, titleKey = "titulo") {
  if (!list.length) {
    setGridMessage(container, "Sin resultados.", "neutral");
    return;
  }

  container.innerHTML = list
    .map(
      (item) =>
        `<article class="card"><h3>${item[titleKey] ?? JSON.stringify(item)}</h3><pre>${JSON.stringify(item, null, 2)}</pre></article>`,
    )
    .join("");
}

async function initProductos() {
  renderLoadingState(grid);
  const productos = await api.productos.getAll();
  renderProductos(productos);
}

async function initClientes() {
  renderLoadingState(clientesGrid);
  const clientes = await api.clientes.getAll();
  renderClientes(clientes);
}

async function initCuentas() {
  renderLoadingState(cuentasGrid);
  const cuentas = await api.cuentas.getAll();
  renderCuentas(cuentas);
}

async function initAuditoria() {
  renderLoadingState(auditoriaGrid);
  const registros = await api.auditoria.getAll();
  renderAuditoria(registros);
}

grid.addEventListener("click", async (event) => {
  const deleteButton = event.target.closest(".btn-delete");
  if (!deleteButton) {
    return;
  }

  const productId = Number(deleteButton.dataset.id);
  if (!confirm("¿Estás seguro de eliminar este producto?")) {
    return;
  }

  const exito = await api.productos.delete(productId);
  if (exito) {
    await initProductos();
  }
});

productSearchForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = Number(document.getElementById("product-search-id").value);
  const producto = await api.productos.getById(id);
  renderProductos(producto ? [producto] : []);
});

productEditForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = Number(document.getElementById("product-edit-id").value);
  const payload = {
    nombre: document.getElementById("product-edit-nombre").value.trim(),
    precio: Number(document.getElementById("product-edit-precio").value),
    stock: Number(document.getElementById("product-edit-stock").value),
    categoria: document.getElementById("product-edit-categoria").value,
  };

  const exito = await api.productos.update(id, payload);
  if (exito) {
    productEditForm.reset();
    await initProductos();
  }
});

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  const nuevoProducto = {
    nombre: document.getElementById("nombre").value.trim(),
    precio: parseFloat(document.getElementById("precio").value),
    stock: parseInt(document.getElementById("stock").value, 10),
    categoria: document.getElementById("categoria").value,
  };

  submitButton.disabled = true;
  submitButton.textContent = "ENVIANDO...";
  setGridMessage(grid, "Registrando producto...", "neutral");

  const exito = await api.productos.create(nuevoProducto);
  if (exito) {
    form.reset();
    await initProductos();
  } else {
    setGridMessage(grid, "No se pudo registrar el producto.", "danger");
  }

  submitButton.disabled = false;
  submitButton.textContent = "INICIALIZAR SECUENCIA";
});

clientesGrid.addEventListener("click", async (event) => {
  const deleteButton = event.target.closest(".btn-client-delete");
  if (deleteButton) {
    const clientId = Number(deleteButton.dataset.id);
    if (!confirm("¿Estás seguro de eliminar este cliente?")) {
      return;
    }

    const exito = await api.clientes.delete(clientId);
    if (exito) {
      await initClientes();
    }
    return;
  }

  const editButton = event.target.closest(".btn-client-edit");
  if (editButton) {
    const clientId = Number(editButton.dataset.id);
    const client = await api.clientes.getById(clientId);
    if (!client) {
      setGridMessage(clientesGrid, "Cliente no encontrado.", "danger");
      return;
    }

    document.getElementById("cliente-edit-id").value = client.id;
    document.getElementById("cliente-edit-dni").value = client.dni ?? "";
    document.getElementById("cliente-edit-cuit").value = client.cuit ?? "";
    document.getElementById("cliente-edit-nombre").value = client.nombre ?? "";
    document.getElementById("cliente-edit-apellido").value =
      client.apellido ?? "";
    document.getElementById("cliente-edit-direccion").value =
      client.direccion ?? "";
    document.getElementById("cliente-edit-fecha").value =
      client.fechaNacimiento ?? "";
    document.getElementById("cliente-edit-localidad").value =
      client.localidad ?? "";
    setGridMessage(
      clientesGrid,
      `Cliente #${client.id} listo para editar.`,
      "neutral",
    );
  }
});

clientesForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    dni: document.getElementById("cliente-dni").value,
    cuit: document.getElementById("cliente-cuit").value,
    nombre: document.getElementById("cliente-nombre").value,
    apellido: document.getElementById("cliente-apellido").value,
    direccion: document.getElementById("cliente-direccion").value,
    fechaNacimiento: document.getElementById("cliente-fecha").value,
    localidad: document.getElementById("cliente-localidad").value,
  };

  const exito = await api.clientes.create(payload);
  if (exito) {
    clientesForm.reset();
    await initClientes();
  }
});

clientesSearchForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const clientId = Number(document.getElementById("cliente-buscar-id").value);
  const cliente = await api.clientes.getById(clientId);
  renderClientes(cliente ? [cliente] : []);
});

clientesEditForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = Number(document.getElementById("cliente-edit-id").value);
  const payload = {
    dni: document.getElementById("cliente-edit-dni").value,
    cuit: document.getElementById("cliente-edit-cuit").value,
    nombre: document.getElementById("cliente-edit-nombre").value,
    apellido: document.getElementById("cliente-edit-apellido").value,
    direccion: document.getElementById("cliente-edit-direccion").value,
    fechaNacimiento: document.getElementById("cliente-edit-fecha").value,
    localidad: document.getElementById("cliente-edit-localidad").value,
  };

  const exito = await api.clientes.update(id, payload);
  if (exito) {
    clientesEditForm.reset();
    await initClientes();
  }
});

cuentasForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    nombreCuenta: document.getElementById("cuenta-nombre").value,
    correoElectronico: document.getElementById("cuenta-correo").value,
    contraseniaPlana: document.getElementById("cuenta-pass").value,
    idCliente: Number(document.getElementById("cuenta-id-cliente").value),
    rolUsuario: document.getElementById("cuenta-rol").value,
    saldo: Number(document.getElementById("cuenta-saldo").value),
  };

  const exito = await api.cuentas.create(payload);
  if (exito) {
    cuentasForm.reset();
    await initCuentas();
  }
});

transferForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    idCuentaOrigen: Number(document.getElementById("origen-id").value),
    idCuentaDestino: Number(document.getElementById("destino-id").value),
    monto: Number(document.getElementById("monto-transferencia").value),
  };

  const exito = await api.cuentas.transferir(payload);
  if (exito) {
    transferForm.reset();
    await initCuentas();
  }
});

cuentaSearchForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const correo = document.getElementById("cuenta-buscar-correo").value.trim();
  const cuenta = await api.cuentas.buscarPorCorreo(correo);
  renderSimple(cuentasGrid, cuenta ? [cuenta] : [], "nombreCuenta");
});

cuentaSearchIdForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = Number(document.getElementById("cuenta-buscar-id").value);
  const cuenta = await api.cuentas.getById(id);
  renderSimple(cuentasGrid, cuenta ? [cuenta] : [], "nombreCuenta");
});

cuentaEditForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = Number(document.getElementById("cuenta-edit-id").value);
  const payload = {
    nombreCuenta: document.getElementById("cuenta-edit-nombre").value.trim(),
    correoElectronico: document
      .getElementById("cuenta-edit-correo")
      .value.trim(),
    contraseniaPlana: document.getElementById("cuenta-edit-pass").value,
    idCliente: Number(document.getElementById("cuenta-edit-id-cliente").value),
    rolUsuario: document.getElementById("cuenta-edit-rol").value,
    saldo: Number(document.getElementById("cuenta-edit-saldo").value),
  };

  const exito = await api.cuentas.update(id, payload);
  if (exito) {
    cuentaEditForm.reset();
    await initCuentas();
  }
});

ventasForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    idCliente: Number(document.getElementById("venta-id-cliente").value),
    tipoPago: document.getElementById("venta-tipo-pago").value,
    descuento: {
      tipo: document.getElementById("venta-descuento-tipo").value,
      valor: Number(
        document.getElementById("venta-descuento-valor").value || 0,
      ),
    },
    items: [
      {
        idProducto: Number(document.getElementById("venta-id-producto").value),
        cantidad: Number(document.getElementById("venta-cantidad").value),
      },
    ],
  };

  const exito = await api.ventas.create(payload);
  if (exito) {
    ventasForm.reset();
    setGridMessage(ventasGrid, "Venta creada correctamente.", "neutral");
  }
});

ventasSearchForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = Number(document.getElementById("venta-search-id").value);
  const venta = await api.ventas.getById(id);
  renderSimple(ventasGrid, venta ? [venta] : [], "id");
});

ventaActionForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = Number(document.getElementById("venta-action-id").value);
  const action = e.submitter.dataset.action;

  if (action === "pagar") {
    const exito = await api.ventas.pagar(id);
    if (exito) {
      setGridMessage(
        ventasGrid,
        `Venta #${id} marcada como pagada.`,
        "neutral",
      );
    }
  }

  if (action === "cancelar") {
    const exito = await api.ventas.cancelar(id);
    if (exito) {
      setGridMessage(ventasGrid, `Venta #${id} cancelada.`, "neutral");
    }
  }
});

auditoriaForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    accion: document.getElementById("auditoria-accion").value,
    idEntidad: Number(document.getElementById("auditoria-id-entidad").value),
    descripcion: document.getElementById("auditoria-descripcion").value,
  };

  const exito = await api.auditoria.create(payload);
  if (exito) {
    auditoriaForm.reset();
    await initAuditoria();
  }
});

auditoriaFilterForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const accion = document
    .getElementById("auditoria-filter-accion")
    .value.trim();
  const registros = await api.auditoria.getByAccion(accion);
  renderAuditoria(registros);
});

auditoriaProductFilterForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const idProducto = Number(
    document.getElementById("auditoria-filter-producto").value,
  );
  const registros = await api.auditoria.getByProducto(idProducto);
  renderAuditoria(registros);
});

stockForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const idProducto = Number(document.getElementById("stock-id-producto").value);
  const resultado = await api.stock.evaluar(idProducto);
  renderSimple(stockGrid, resultado ? [resultado] : [], "producto");
});

recomendacionesForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    idProductoActual: Number(document.getElementById("reco-id-producto").value),
    precioMin: Number(document.getElementById("reco-precio-min").value),
    precioMax: Number(document.getElementById("reco-precio-max").value),
  };

  const recomendaciones = await api.recomendaciones.getForProduct(payload);
  renderSimple(recomendacionesGrid, recomendaciones || [], "nombre");
});

initProductos();
initClientes();
initCuentas();
initAuditoria();
