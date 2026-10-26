import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const app = readFileSync(join(raiz, 'src', 'js', 'app.js'), 'utf8');

function cargarEscaparHtml() {
    const inicio = app.indexOf('function escaparHtml(');
    const fin = app.indexOf('\n}', inicio);
    assert.ok(inicio >= 0 && fin > inicio, 'escaparHtml debe existir en app.js');
    const contexto = {};
    vm.createContext(contexto);
    vm.runInContext(`${app.slice(inicio, fin + 2)}\nglobalThis.escaparHtml = escaparHtml;`, contexto);
    return contexto.escaparHtml;
}

test('escaparHtml neutraliza etiquetas y atributos', () => {
    const escaparHtml = cargarEscaparHtml();

    assert.equal(
        escaparHtml('<img src=x onerror="alert(\'x\')">'),
        '&lt;img src=x onerror=&quot;alert(&#39;x&#39;)&quot;&gt;');
    assert.equal(escaparHtml(null), '');
});

test('el resumen del dashboard escapa acción, descripción y fecha', () => {
    const inicio = app.indexOf('async function renderDashboard');
    const fin = app.indexOf('function renderKPIs', inicio);
    assert.ok(inicio >= 0 && fin > inicio, 'renderDashboard debe existir en app.js');
    const dashboard = app.slice(inicio, fin);

    assert.ok(dashboard.includes('${escaparHtml(log.accion)}'));
    assert.ok(dashboard.includes('${escaparHtml(log.descripcion)}'));
    assert.ok(dashboard.includes("${escaparHtml(log.fecha || 'Reciente')}"));
    assert.ok(!dashboard.includes('${log.accion}'));
    assert.ok(!dashboard.includes('${log.descripcion}'));
    assert.ok(!dashboard.includes("${log.fecha || 'Reciente'}"));
});
