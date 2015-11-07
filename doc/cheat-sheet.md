# test.check cheatsheet

<table>
  <thead>
    <th colspan="3">Simple Generators</th>
  </thead>
  <thead>
    <th>Thing</th><th>Args</th><th>What it do</th>
  </thead>

  <tr>
    <td><code>(gen/return x)</code></td>
    <td><code>x: any value</code></td>
    <td>A constant generator that always generates <code>x</code></td>
  </tr>
  <tr>
    <td><code>gen/nat</code></td>
    <td>N/A</td>
    <td>Generates small non-negative integers (useful for generating
    sizes of things)</td>
  </tr>

</table>
