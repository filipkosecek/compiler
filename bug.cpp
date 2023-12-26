byte g(byte v[][][], byte z[])
{
	z[0] = !v[0][0][1];
}

void f(byte v[][][], byte z[], byte &x, int i, int j[])
{
	x = g(v, z);
	i = (int) x;
	j = (int[]) v[0][0];
	i = j[0];
}

void main(byte v[][][], byte z[], byte &x, int i)
{
	i;
	v[i][0][1] = x;
/*	z = "caves kali";
	z[0];
	x = 3;
	f();*/
}
