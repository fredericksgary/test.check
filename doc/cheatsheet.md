# test.check cheatsheet

<table>
<thead><th colspan="3">Simple Generators</th></thead>
<thead><th>Thing</th><th>Args</th><th>What it do</th></thead>
<tr><td><code>(gen/return x)</code></td><td><code>x</code> - any value</td><td>A constant generator that always generates <code>x</code>.</td></tr>
<tr><td><code>gen/boolean</code></td><td>N/A</td><td>Generates booleans (<code>true</code> and <code>false</code>).</td></tr>
<tr><td><code>gen/nat</code></td><td>N/A</td><td>Generates small non-negative integers (useful for generating sizes of things).</td></tr>
<tr><td><code>gen/large-integer</code></td><td>N/A</td><td>Generates a large range of integers</td></tr>
<tr><td><code>(gen/large-integer* {.. ..})</code></td><td><code>:min</code> - a minimum value<br /><code>:max</code> - a maximum value</td><td>Generates a large range of integers</td></tr>
<tr><td><code>gen/double</code></td><td>N/A</td><td>Generates a double from the full range, including infinities and <code>NaN</code>.</td></tr>
<tr><td><code>(gen/double* {.. ..})</code></td><td></td><td></td></tr>
</table>
